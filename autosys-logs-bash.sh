#!/bin/bash
#
# Autosys Log Retriever with Authentication (Bash version)
#
# This script retrieves stdout and stderr logs for a specified Autosys job's most recent run.
# It uses explicit authentication parameters rather than relying on pre-configured environments.
#
# Usage:
#   ./autosys_logs.sh -j JOB_NAME [-u USERNAME] [-p PASSWORD] [-i INSTANCE] [-s SERVER]
#
# Author: Claude
# Date: April 9, 2025
#

# Print banner
print_banner() {
    echo
    echo "====================================="
    echo "      AUTOSYS LOG RETRIEVER          "
    echo "====================================="
    echo
}

# Print usage information
print_usage() {
    echo "Usage: $0 [options] [job_name]"
    echo "  or:  $0 [options] -j JOB_NAME"
    echo
    echo "Options:"
    echo "  -j, --job JOB_NAME      Name of the Autosys job"
    echo "  -u, --user USERNAME     Autosys username"
    echo "  -p, --pass PASSWORD     Autosys password (not recommended, use -u without -p for secure prompt)"
    echo "  -i, --instance INSTANCE Autosys instance name"
    echo "  -s, --server SERVER     Autosys server (optional)"
    echo "  -h, --help              Display this help message"
    echo
    echo "Notes:"
    echo "  - If username is provided without password, script will prompt securely"
    echo "  - If no authentication is provided, script will use environment settings"
    echo
}

# Get job details using autorep command
get_job_details() {
    local job_name="$1"
    local username="$2"
    local password="$3"
    local instance="$4"
    local server="$5"
    
    # Build the command with authentication if provided
    cmd=("autorep" "-j" "$job_name" "-L")
    
    if [ -n "$username" ]; then
        cmd+=("-u" "$username")
    fi
    
    if [ -n "$password" ]; then
        cmd+=("-p" "$password")
    fi
    
    if [ -n "$instance" ]; then
        cmd+=("-i" "$instance")
    fi
    
    if [ -n "$server" ]; then
        cmd+=("-s" "$server")
    fi
    
    # Run the command and capture output
    output=$("${cmd[@]}" 2>/tmp/autosys_error.$$)
    exit_code=$?
    
    # Check for errors
    if [ $exit_code -ne 0 ]; then
        echo "Error running autorep command. Exit code: $exit_code" >&2
        echo "Error output: $(cat /tmp/autosys_error.$$)" >&2
        rm -f /tmp/autosys_error.$$
        return 1
    fi
    
    rm -f /tmp/autosys_error.$$
    
    # Parse output to extract job details
    status=$(echo "$output" | grep -E "Status/Event:" | sed -E 's/.*Status\/Event:\s+(\w+).*/\1/')
    last_run=$(echo "$output" | grep -E "Last Run:" | sed -E 's/.*Last Run:\s+([\d\/]+\s+[\d:]+).*/\1/')
    std_out_file=$(echo "$output" | grep -E "std_out_file:" | sed -E 's/.*std_out_file:\s*(.*?)(\s+|$).*/\1/')
    std_err_file=$(echo "$output" | grep -E "std_err_file:" | sed -E 's/.*std_err_file:\s*(.*?)(\s+|$).*/\1/')
    
    # If stdout/stderr files not found, try to construct paths based on job directory
    if [ -z "$std_out_file" ] || [ -z "$std_err_file" ]; then
        job_dir=$(echo "$output" | grep -E "job_dir:" | sed -E 's/.*job_dir:\s*(.*?)(\s+|$).*/\1/')
        
        if [ -n "$job_dir" ]; then
            if [ -z "$std_out_file" ]; then
                std_out_file="${job_dir}/${job_name}.out"
            fi
            
            if [ -z "$std_err_file" ]; then
                std_err_file="${job_dir}/${job_name}.err"
            fi
        fi
    fi
    
    # Store job details in global variables
    JOB_NAME="$job_name"
    JOB_STATUS="$status"
    JOB_LAST_RUN="$last_run"
    JOB_STDOUT_FILE="$std_out_file"
    JOB_STDERR_FILE="$std_err_file"
    
    return 0
}

# Display job logs
display_job_logs() {
    # Print job information header
    echo
    echo "================================================================================"
    echo "Job Name: $JOB_NAME"
    echo "Status: ${JOB_STATUS:-Unknown}"
    echo "Last Run: ${JOB_LAST_RUN:-Unknown}"
    echo "================================================================================"
    echo
    
    # Handle stdout file
    if [ -n "$JOB_STDOUT_FILE" ]; then
        echo
        echo "STDOUT LOG ($JOB_STDOUT_FILE):"
        echo "--------------------------------------------------------------------------------"
        
        # Try to read the file directly
        if [ -f "$JOB_STDOUT_FILE" ] && [ -r "$JOB_STDOUT_FILE" ]; then
            cat "$JOB_STDOUT_FILE"
        else
            # If direct access fails, try to retrieve via Autosys utilities
            echo "Direct access to stdout file failed. Attempting to retrieve via Autosys utilities..."
            retrieve_log_via_autosys "$JOB_NAME" "stdout"
        fi
    else
        echo
        echo "STDOUT LOG: Not available"
    fi
    
    # Handle stderr file
    if [ -n "$JOB_STDERR_FILE" ]; then
        echo
        echo "STDERR LOG ($JOB_STDERR_FILE):"
        echo "--------------------------------------------------------------------------------"
        
        # Try to read the file directly
        if [ -f "$JOB_STDERR_FILE" ] && [ -r "$JOB_STDERR_FILE" ]; then
            cat "$JOB_STDERR_FILE"
        else
            # If direct access fails, try to retrieve via Autosys utilities
            echo "Direct access to stderr file failed. Attempting to retrieve via Autosys utilities..."
            retrieve_log_via_autosys "$JOB_NAME" "stderr"
        fi
    else
        echo
        echo "STDERR LOG: Not available"
    fi
}

# Retrieve logs using Autosys utilities
retrieve_log_via_autosys() {
    local job_name="$1"
    local log_type="$2"
    local log_option
    
    # Set log option based on type
    if [ "$log_type" = "stdout" ]; then
        log_option="-o"
    else
        log_option="-e"
    fi
    
    # Build command with authentication if provided
    cmd=("autosyslog" "-j" "$job_name" "$log_option")
    
    if [ -n "$USERNAME" ]; then
        cmd+=("-u" "$USERNAME")
    fi
    
    if [ -n "$PASSWORD" ]; then
        cmd+=("-p" "$PASSWORD")
    fi
    
    if [ -n "$INSTANCE" ]; then
        cmd+=("-i" "$INSTANCE")
    fi
    
    if [ -n "$SERVER" ]; then
        cmd+=("-s" "$SERVER")
    fi
    
    # Run the command
    "${cmd[@]}"
    exit_code=$?
    
    if [ $exit_code -ne 0 ]; then
        echo "Error retrieving logs via autosyslog. Exit code: $exit_code" >&2
        return 1
    fi
    
    return 0
}

# Main function
main() {
    local job_name=""
    local username=""
    local password=""
    local instance=""
    local server=""
    
    # Parse command line arguments
    while [ $# -gt 0 ]; do
        case "$1" in
            -j|--job)
                job_name="$2"
                shift 2
                ;;
            -u|--user)
                username="$2"
                shift 2
                ;;
            -p|--pass)
                password="$2"
                shift 2
                ;;
            -i|--instance)
                instance="$2"
                shift 2
                ;;
            -s|--server)
                server="$2"
                shift 2
                ;;
            -h|--help)
                print_usage
                exit 0
                ;;
            -*)
                echo "Error: Unknown option: $1" >&2
                print_usage
                exit 1
                ;;
            *)
                # If no job name is set yet and argument doesn't start with -, assume it's the job name
                if [ -z "$job_name" ]; then
                    job_name="$1"
                else
                    echo "Error: Unexpected argument: $1" >&2
                    print_usage
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # Validate required arguments
    if [ -z "$job_name" ]; then
        echo "Error: Job name is required" >&2
        print_usage
        exit 1
    fi
    
    # If username is provided but password isn't, prompt for password
    if [ -n "$username" ] && [ -z "$password" ]; then
        # Use read with -s option to hide input
        echo -n "Enter password for $username: " >&2
        read -s password
        echo >&2  # Add a newline after password input
    fi
    
    # If username and password are provided but instance isn't, prompt for instance
    if [ -n "$username" ] && [ -n "$password" ] && [ -z "$instance" ]; then
        echo -n "Enter Autosys instance name: " >&2
        read instance
    fi
    
    # Store authentication in global variables for other functions
    USERNAME="$username"
    PASSWORD="$password"
    INSTANCE="$instance"
    SERVER="$server"
    
    # Print banner
    print_banner
    echo "Retrieving logs for Autosys job: $job_name"
    
    # Get job details and display logs
    if get_job_details "$job_name" "$username" "$password" "$instance" "$server"; then
        display_job_logs
        return 0
    else
        echo "Failed to retrieve job details for $job_name" >&2
        return 1
    fi
}

# Call main function with all arguments
main "$@"
