#!/usr/bin/env python3
"""
Autosys Log Retriever with Authentication

This script retrieves stdout and stderr logs for a specified Autosys job's most recent run.
It uses explicit authentication parameters rather than relying on pre-configured environments.

Usage:
    python autosys_logs.py -j JOB_NAME [-u USERNAME] [-p PASSWORD] [-i INSTANCE] [-s SERVER]

Author: Claude
Date: April 9, 2025
"""

import os
import sys
import subprocess
import re
import tempfile
import shutil
import argparse
import getpass
from pathlib import Path
from typing import Dict, List, Optional, Union, Tuple


class AutosysLogRetriever:
    """
    Class to retrieve Autosys job logs with explicit authentication.
    """

    def __init__(self, username: Optional[str] = None, password: Optional[str] = None, 
                 instance: Optional[str] = None, server: Optional[str] = None):
        """
        Initialize the log retriever with optional authentication parameters.
        
        Args:
            username: Autosys username
            password: Autosys password
            instance: Autosys instance name
            server: Autosys server address
        """
        self.username = username
        self.password = password
        self.instance = instance
        self.server = server
        
        # Define regex patterns for parsing autorep output
        self.STATUS_PATTERN = re.compile(r"Status/Event:\s+(\w+)")
        self.LAST_RUN_PATTERN = re.compile(r"Last Run:\s+([\d/]+\s+[\d:]+)")
        self.STD_OUT_FILE_PATTERN = re.compile(r"std_out_file:\s*(.*?)(?:\s+|$)")
        self.STD_ERR_FILE_PATTERN = re.compile(r"std_err_file:\s*(.*?)(?:\s+|$)")
        self.JOB_DIR_PATTERN = re.compile(r"job_dir:\s*(.*?)(?:\s+|$)")
    
    def get_job_details(self, job_name: str) -> Dict[str, str]:
        """
        Get details about the specified Autosys job using autorep command with authentication.
        
        Args:
            job_name: Name of the Autosys job
            
        Returns:
            Dictionary containing job details
            
        Raises:
            subprocess.CalledProcessError: If autorep command fails
            RuntimeError: If job details cannot be retrieved
        """
        # Build the command with authentication parameters if provided
        cmd = ["autorep", "-j", job_name, "-L"]
        
        if self.username:
            cmd.extend(["-u", self.username])
            
        if self.password:
            cmd.extend(["-p", self.password])
            
        if self.instance:
            cmd.extend(["-i", self.instance])
            
        if self.server:
            cmd.extend(["-s", self.server])
            
        try:
            # Run the autorep command
            result = subprocess.run(
                cmd, 
                capture_output=True, 
                text=True, 
                check=True
            )
            output = result.stdout
            
            # Initialize job details dictionary
            job_details = {
                "job_name": job_name,
                "status": None,
                "last_run": None,
                "std_out_file": None,
                "std_err_file": None
            }
            
            # Extract job status
            status_match = self.STATUS_PATTERN.search(output)
            if status_match:
                job_details["status"] = status_match.group(1)
            
            # Extract last run time
            last_run_match = self.LAST_RUN_PATTERN.search(output)
            if last_run_match:
                job_details["last_run"] = last_run_match.group(1)
            
            # Extract stdout and stderr file paths
            std_out_match = self.STD_OUT_FILE_PATTERN.search(output)
            if std_out_match and std_out_match.group(1):
                job_details["std_out_file"] = std_out_match.group(1).strip()
            
            std_err_match = self.STD_ERR_FILE_PATTERN.search(output)
            if std_err_match and std_err_match.group(1):
                job_details["std_err_file"] = std_err_match.group(1).strip()
            
            # If stdout/stderr files not found, try to construct paths based on other configurations
            if not job_details["std_out_file"] or not job_details["std_err_file"]:
                log_dir_match = self.JOB_DIR_PATTERN.search(output)
                if log_dir_match:
                    log_dir = log_dir_match.group(1).strip()
                    if not job_details["std_out_file"]:
                        job_details["std_out_file"] = os.path.join(log_dir, f"{job_name}.out")
                    if not job_details["std_err_file"]:
                        job_details["std_err_file"] = os.path.join(log_dir, f"{job_name}.err")
            
            return job_details
        
        except subprocess.CalledProcessError as e:
            print(f"Error retrieving job details: {e}")
            print(f"stderr: {e.stderr}")
            raise
        except Exception as e:
            print(f"Unexpected error: {e}")
            raise RuntimeError(f"Failed to retrieve job details: {e}")
    
    def get_job_logs(self, job_details: Dict[str, str]) -> None:
        """
        Retrieve and display job logs based on job details.
        
        Args:
            job_details: Dictionary containing job details
        """
        # Create a temporary directory to store log copies if needed
        temp_dir = tempfile.mkdtemp(prefix="autosys_logs_")
        
        try:
            # Print job information header
            print("\n" + "="*80)
            print(f"Job Name: {job_details['job_name']}")
            print(f"Status: {job_details.get('status', 'Unknown')}")
            print(f"Last Run: {job_details.get('last_run', 'Unknown')}")
            print("="*80 + "\n")
            
            # Handle stdout file
            if job_details.get("std_out_file"):
                stdout_path = job_details["std_out_file"]
                print(f"\nSTDOUT LOG ({stdout_path}):")
                print("-"*80)
                
                try:
                    # Try to read the file directly
                    if os.path.exists(stdout_path):
                        with open(stdout_path, 'r') as f:
                            print(f.read())
                    # If direct access fails, try to copy using autosys utilities
                    else:
                        print(f"Direct access to stdout file failed. Attempting to retrieve via Autosys utilities...")
                        self.retrieve_log_via_autosys(job_details['job_name'], is_stdout=True)
                except Exception as e:
                    print(f"Error accessing stdout log: {e}")
            else:
                print("\nSTDOUT LOG: Not available")
            
            # Handle stderr file
            if job_details.get("std_err_file"):
                stderr_path = job_details["std_err_file"]
                print(f"\nSTDERR LOG ({stderr_path}):")
                print("-"*80)
                
                try:
                    # Try to read the file directly
                    if os.path.exists(stderr_path):
                        with open(stderr_path, 'r') as f:
                            print(f.read())
                    # If direct access fails, try to copy using autosys utilities
                    else:
                        print(f"Direct access to stderr file failed. Attempting to retrieve via Autosys utilities...")
                        self.retrieve_log_via_autosys(job_details['job_name'], is_stdout=False)
                except Exception as e:
                    print(f"Error accessing stderr log: {e}")
            else:
                print("\nSTDERR LOG: Not available")
        
        finally:
            # Clean up temporary directory
            shutil.rmtree(temp_dir, ignore_errors=True)
    
    def retrieve_log_via_autosys(self, job_name: str, is_stdout: bool) -> None:
        """
        Retrieve logs using Autosys utilities with authentication.
        
        Args:
            job_name: Name of the job
            is_stdout: True for stdout, False for stderr
        """
        # Build command with authentication parameters if provided
        cmd = ["autosyslog", "-j", job_name, "-o" if is_stdout else "-e"]
        
        if self.username:
            cmd.extend(["-u", self.username])
            
        if self.password:
            cmd.extend(["-p", self.password])
            
        if self.instance:
            cmd.extend(["-i", self.instance])
            
        if self.server:
            cmd.extend(["-s", self.server])
        
        try:
            # Run the command and capture output
            result = subprocess.run(cmd, capture_output=True, text=True, check=True)
            print(result.stdout)
        except subprocess.CalledProcessError as e:
            print(f"Error running autosyslog command. Exit code: {e.returncode}")
            print(f"Error message: {e.stderr}")

def parse_arguments():
    """
    Parse command line arguments.
    
    Returns:
        Parsed arguments object
    """
    parser = argparse.ArgumentParser(
        description="Retrieve stdout and stderr logs for Autosys jobs with authentication"
    )
    
    parser.add_argument(
        "-j", "--job", 
        dest="job_name",
        help="Name of the Autosys job"
    )
    
    parser.add_argument(
        "-u", "--user", 
        dest="username",
        help="Autosys username"
    )
    
    parser.add_argument(
        "-p", "--password", 
        dest="password",
        help="Autosys password (not recommended, use -u without -p for secure prompt)"
    )
    
    parser.add_argument(
        "-i", "--instance", 
        dest="instance",
        help="Autosys instance name"
    )
    
    parser.add_argument(
        "-s", "--server", 
        dest="server",
        help="Autosys server (optional)"
    )
    
    # Allow specifying job name positionally
    parser.add_argument(
        "job_name_positional", 
        nargs="?",
        help="Name of the Autosys job (if not specified with -j)"
    )
    
    args = parser.parse_args()
    
    # Use positional job name if -j/--job not provided
    if not args.job_name and args.job_name_positional:
        args.job_name = args.job_name_positional
    
    # Validate required arguments
    if not args.job_name:
        parser.error("Job name is required. Use -j/--job or provide as positional argument.")
    
    return args

def main():
    """
    Main function to process command line arguments and retrieve logs.
    """
    args = parse_arguments()
    
    # Print banner
    print(f"\n{'='*20} Autosys Log Retriever {'='*20}")
    print(f"Retrieving logs for job: {args.job_name}")
    
    # If username is provided but password isn't, prompt for password
    username = args.username
    password = args.password
    instance = args.instance
    
    if username and not password:
        password = getpass.getpass(f"Enter password for {username}: ")
    
    # If username and no instance, prompt for instance
    if username and not instance:
        instance = input("Enter Autosys instance name: ")
    
    try:
        # Initialize log retriever with authentication parameters
        retriever = AutosysLogRetriever(
            username=username,
            password=password,
            instance=instance,
            server=args.server
        )
        
        # Get job details and logs
        job_details = retriever.get_job_details(args.job_name)
        retriever.get_job_logs(job_details)
        return 0
    
    except Exception as e:
        print(f"Error: {e}")
        return 1

if __name__ == "__main__":
    sys.exit(main())
