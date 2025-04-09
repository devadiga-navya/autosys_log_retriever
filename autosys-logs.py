#!/usr/bin/env python3
"""
Autosys Log Retriever

This script retrieves stdout and stderr logs for a specified Autosys job's most recent run.
It uses standard Python libraries and Autosys command-line utilities.

Usage:
    python autosys_logs.py <job_name>
"""

import os
import sys
import subprocess
import re
import tempfile
import datetime
import shutil
from pathlib import Path


def get_job_details(job_name):
    """
    Get details about the specified Autosys job using autorep command.
    
    Args:
        job_name (str): Name of the Autosys job
        
    Returns:
        dict: Job details including status, last run, etc.
    """
    try:
        cmd = ["autorep", "-j", job_name, "-L"]
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
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
        status_match = re.search(r"Status/Event: (\w+)", output)
        if status_match:
            job_details["status"] = status_match.group(1)
        
        # Extract last run time
        last_run_match = re.search(r"Last Run: ([\d\/]+\s+[\d:]+)", output)
        if last_run_match:
            job_details["last_run"] = last_run_match.group(1)
        
        # Extract stdout and stderr file paths
        std_out_match = re.search(r"std_out_file:\s*(.*?)(?:\s+|$)", output)
        if std_out_match and std_out_match.group(1):
            job_details["std_out_file"] = std_out_match.group(1)
        
        std_err_match = re.search(r"std_err_file:\s*(.*?)(?:\s+|$)", output)
        if std_err_match and std_err_match.group(1):
            job_details["std_err_file"] = std_err_match.group(1)
        
        # If stdout/stderr files not found, try to construct paths based on other configurations
        if not job_details["std_out_file"] or not job_details["std_err_file"]:
            log_dir_match = re.search(r"job_dir:\s*(.*?)(?:\s+|$)", output)
            if log_dir_match:
                log_dir = log_dir_match.group(1)
                if not job_details["std_out_file"]:
                    job_details["std_out_file"] = os.path.join(log_dir, f"{job_name}.out")
                if not job_details["std_err_file"]:
                    job_details["std_err_file"] = os.path.join(log_dir, f"{job_name}.err")
        
        return job_details
    
    except subprocess.CalledProcessError as e:
        print(f"Error retrieving job details: {e}")
        print(f"stderr: {e.stderr}")
        sys.exit(1)
    except Exception as e:
        print(f"Unexpected error: {e}")
        sys.exit(1)


def get_job_logs(job_details):
    """
    Retrieve and display job logs based on job details.
    
    Args:
        job_details (dict): Dictionary containing job details
    """
    # Create a temporary directory to store log copies if needed
    temp_dir = tempfile.mkdtemp(prefix="autosys_logs_")
    
    try:
        # Print job information header
        print("\n" + "="*80)
        print(f"Job Name: {job_details['job_name']}")
        print(f"Status: {job_details['status']}")
        print(f"Last Run: {job_details['last_run']}")
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
                    cmd = ["autosyslog", "-j", job_details['job_name'], "-o"]
                    subprocess.run(cmd, check=True)
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
                    cmd = ["autosyslog", "-j", job_details['job_name'], "-e"]
                    subprocess.run(cmd, check=True)
            except Exception as e:
                print(f"Error accessing stderr log: {e}")
        else:
            print("\nSTDERR LOG: Not available")
    
    finally:
        # Clean up temporary directory
        shutil.rmtree(temp_dir, ignore_errors=True)


def main():
    """
    Main function to process command line arguments and retrieve logs
    """
    # Check for correct usage
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <job_name>")
        sys.exit(1)
    
    job_name = sys.argv[1]
    print(f"Retrieving logs for Autosys job: {job_name}")
    
    # Get job details and logs
    job_details = get_job_details(job_name)
    get_job_logs(job_details)


if __name__ == "__main__":
    main()
