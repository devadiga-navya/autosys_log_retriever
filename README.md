python minimal_autosys_logs.py --job daily_backup
python minimal_autosys_logs.py --job weekly_report --run 42 --pretty



{
  "job_name": "job_name_here",
  "success": true,
  "status": "job_status",
  "last_run": {
    "id": 12345,
    "status": "run_status",
    "start_time": "start_time_string",
    "end_time": "end_time_string"
  },
  "logs": {
    "stdout": "stdout_content_here",
    "stderr": "stderr_content_here"
  }
}






# Basic usage (uses environment settings if available)
python autosys_logs.py my_job_name

# With username (will prompt for password securely)
python autosys_logs.py -j my_job_name -u my_username

# Full authentication parameters
python autosys_logs.py -j my_job_name -u my_username -p my_password -i my_instance -s my_server

# Mixed positional and named arguments
python autosys_logs.py my_job_name -u my_username -i my_instance



# With command-line authentication
java AutosysLogRetriever -j your_job_name -u your_username -p your_password -i your_instance

# With password prompt (more secure)
java AutosysLogRetriever -j your_job_name -u your_username -i your_instance

# Still works with environment settings (if already configured)
java AutosysLogRetriever your_job_name
