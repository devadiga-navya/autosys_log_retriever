#!/usr/bin/env python3
"""
AutoSys System Log Collector

This script collects only AutoSys system logs for a job and returns them as JSON.
It focuses purely on the system logs without retrieving stdout or stderr files.

Usage: python autosys_system_logs.py --job JOB_NAME [--run RUN_NUMBER] [--format {json,text}]
"""

import argparse
import json
import subprocess
import sys
from datetime import datetime


def run_command(command):
    """Execute a shell command and return its output."""
    try:
        result = subprocess.run(
            command,
            shell=True,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        return result.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"Error running command: {command}", file=sys.stderr)
        print(f"Error message: {e.stderr.strip()}", file=sys.stderr)
        return None


def get_system_logs(job_name, run_number=None):
    """Get AutoSys system logs for the job."""
    # Get job info
    job_info = run_command(f"autorep -j {job_name} -q")
    
    # Get run logs based on whether a run number was specified
    if run_number:
        # Get specific run logs
        system_logs = run_command(f"autosyslog -j {job_name} -r {run_number}")
        run_details = run_command(f"autorep -j {job_name} -r {run_number}")
    else:
        # Get most recent logs
        system_logs = run_command(f"autosyslog -j {job_name}")
        run_details = run_command(f"autorep -j {job_name} -l")
    
    # Get job status history (useful for troubleshooting)
    status_history = run_command(f"autorep -j {job_name} -s")
    
    return {
        "job_info": job_info,
        "system_logs": system_logs,
        "run_details": run_details,
        "status_history": status_history
    }


def extract_job_metadata(job_info):
    """Extract useful metadata from job info text."""
    metadata = {}
    
    if not job_info:
        return metadata
    
    # Extract common job attributes
    attributes = [
        "machine", "box_name", "command", "condition", 
        "date_conditions", "days_of_week", "start_times",
        "job_type", "priority", "max_run_alarm", "alarm_if_fail"
    ]
    
    for attr in attributes:
        # Look for the attribute in the job info
        import re
        match = re.search(rf"{attr}:\s*(.*?)(\s|$)", job_info)
        if match:
            metadata[attr] = match.group(1)
    
    return metadata


def main():
    """Main function to collect and output logs as JSON."""
    parser = argparse.ArgumentParser(description="Collect AutoSys system logs and output as JSON")
    parser.add_argument("--job", required=True, help="AutoSys job name")
    parser.add_argument("--run", type=int, help="Specific run number (optional)")
    parser.add_argument("--format", choices=["json", "text"], default="json", 
                        help="Output format (json or text, default: json)")
    
    args = parser.parse_args()
    job_name = args.job
    run_number = args.run
    output_format = args.format
    
    # Get system logs and job information
    logs_data = get_system_logs(job_name, run_number)
    
    # Extract useful metadata from job info
    metadata = extract_job_metadata(logs_data["job_info"])
    
    # Prepare output
    output = {
        "job_name": job_name,
        "run_number": run_number,
        "timestamp": datetime.now().isoformat(),
        "metadata": metadata,
        "system_logs": logs_data["system_logs"],
        "run_details": logs_data["run_details"],
        "status_history": logs_data["status_history"]
    }
    
    # Output based on format preference
    if output_format == "json":
        print(json.dumps(output, indent=2))
    else:
        # Text format - more human readable
        print(f"=== AutoSys System Logs for Job: {job_name} ===")
        print(f"Run Number: {run_number if run_number else 'Latest'}")
        print(f"Timestamp: {output['timestamp']}")
        print("\n=== Job Metadata ===")
        for key, value in metadata.items():
            print(f"{key}: {value}")
        print("\n=== Run Details ===")
        print(logs_data["run_details"])
        print("\n=== Status History ===")
        print(logs_data["status_history"])
        print("\n=== System Logs ===")
        print(logs_data["system_logs"])


if __name__ == "__main__":
    main()
