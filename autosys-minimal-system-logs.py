#!/usr/bin/env python3
"""
Minimal AutoSys System Log Collector

This script collects only AutoSys system logs and returns them as JSON with minimal information.
Usage: python minimal_autosys_logs.py --job JOB_NAME [--run RUN_NUMBER]
"""

import argparse
import json
import subprocess


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
        print(f"Error running command: {command}")
        print(f"Error message: {e.stderr.strip()}")
        return None


def get_system_logs(job_name, run_number=None):
    """Get AutoSys system logs for the job."""
    if run_number:
        command = f"autosyslog -j {job_name} -r {run_number}"
    else:
        command = f"autosyslog -j {job_name}"
    
    system_logs = run_command(command)
    
    # Prepare minimal results
    results = {
        "job_name": job_name,
        "run_number": run_number,
        "system_logs": system_logs
    }
    
    return results


def main():
    """Main function to collect and output logs as JSON."""
    parser = argparse.ArgumentParser(description="Collect AutoSys system logs with minimal information")
    parser.add_argument("--job", required=True, help="AutoSys job name")
    parser.add_argument("--run", type=int, help="Specific run number (optional)")
    parser.add_argument("--pretty", action="store_true", help="Pretty print the JSON output")
    
    args = parser.parse_args()
    job_name = args.job
    run_number = args.run
    pretty_print = args.pretty
    
    # Get system logs
    log_data = get_system_logs(job_name, run_number)
    
    # Output as JSON
    if pretty_print:
        print(json.dumps(log_data, indent=2))
    else:
        print(json.dumps(log_data))


if __name__ == "__main__":
    main()
