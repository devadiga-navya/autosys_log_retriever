#!/bin/bash
# =============================================================================
# AutoSys Log Retriever
# Description: This script retrieves stdout and stderr logs for AutoSys jobs
# Author: Claude
# Date: 2025-04-09
# =============================================================================

# ANSI color codes for better readability
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Function to display banner
show_banner() {
    echo -e "${CYAN}"
    echo "╔════════════════════════════════════════════════════════╗"
    echo "║                 AutoSys Log Retriever                  ║"
    echo "╚════════════════════════════════════════════════════════╝"
    echo -e "${NC}"
}

# Function to display usage
usage() {
    echo -e "${YELLOW}Usage:${NC}"
    echo -e "  ${0} [-j job_name] [-r run_num] [-a] [-h]"
    echo ""
    echo -e "${YELLOW}Options:${NC}"
    echo -e "  -j JOB_NAME   Specify the AutoSys job name"
    echo -e "  -r RUN_NUM    Specify run number (default: last run)"
    echo -e "  -a            Show all runs for the job"
    echo -e "  -h            Display this help message"
    echo ""
}

# Function to check if autosys commands are available
check_autosys() {
    echo -e "${BLUE}Checking AutoSys availability...${NC}"
    
    # Check if autorep command exists
    if ! command -v autorep &> /dev/null; then
        echo -e "${RED}Error: AutoSys commands not found.${NC}"
        echo -e "Make sure AutoSys client is installed and environment is properly set up."
        echo -e "You may need to source the AutoSys environment script first:"
        echo -e "${YELLOW}source /path/to/autosys/env.sh${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}AutoSys is available.${NC}"
    return 0
}

# Function to validate job exists
validate_job() {
    local job_name=$1
    
    echo -e "${BLUE}Validating job '${job_name}'...${NC}"
    
    # Use autorep to check if job exists
    autorep -j "${job_name}" &> /dev/null
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Error: Job '${job_name}' not found in AutoSys.${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}Job '${job_name}' exists.${NC}"
    return 0
}

# Function to get job details
get_job_details() {
    local job_name=$1
    
    echo -e "${BLUE}Retrieving job details for '${job_name}'...${NC}"
    
    # Get job details with autorep
    local job_info=$(autorep -j "${job_name}" -q)
    
    # Display job information
    echo -e "${CYAN}Job Information:${NC}"
    echo "$job_info" | sed 's/^/  /'
    
    return 0
}

# Function to get log locations for a job
get_log_locations() {
    local job_name=$1
    local run_num=$2
    
    echo -e "${BLUE}Finding log files for job '${job_name}'...${NC}"
    
    # Get job details with more detailed information
    local job_detail=$(autorep -j "${job_name}" -q)
    
    # Extract stdout and stderr file paths
    local std_out=$(echo "$job_detail" | grep -i "std_out_file:" | awk -F ":" '{print $2}' | sed 's/^[ \t]*//')
    local std_err=$(echo "$job_detail" | grep -i "std_err_file:" | awk -F ":" '{print $2}' | sed 's/^[ \t]*//')
    
    # If std_out not found in autorep output, try to get from job definition
    if [ -z "$std_out" ]; then
        std_out=$(autosyslog -J "${job_name}" 2>/dev/null | grep -i "std_out_file:" | awk -F ":" '{print $2}' | sed 's/^[ \t]*//')
    fi
    
    # If std_err not found in autorep output, try to get from job definition
    if [ -z "$std_err" ]; then
        std_err=$(autosyslog -J "${job_name}" 2>/dev/null | grep -i "std_err_file:" | awk -F ":" '{print $2}' | sed 's/^[ \t]*//')
    fi
    
    # Check if we found the log paths
    if [ -z "$std_out" ] && [ -z "$std_err" ]; then
        echo -e "${RED}Warning: Could not find log file paths in job definition.${NC}"
        echo -e "${YELLOW}Trying to locate logs in default AutoSys log directory...${NC}"
        
        # Try to find logs in common AutoSys log locations
        local autosys_log_dir="/opt/CA/WorkloadAutomationAE/systemagent/WA_AGENT/logs"
        if [ -d "$autosys_log_dir" ]; then
            std_out=$(find "$autosys_log_dir" -name "${job_name}*.std_out" -type f | sort | tail -1)
            std_err=$(find "$autosys_log_dir" -name "${job_name}*.std_err" -type f | sort | tail -1)
        fi
    fi
    
    # If we still can't find logs, try to check event server logs
    if [ -z "$std_out" ] && [ -z "$std_err" ]; then
        echo -e "${YELLOW}Checking AutoSys event server logs for job execution details...${NC}"
        local event_detail=$(autosyslog -j "${job_name}" -r)
        echo "$event_detail" | grep -i "Log File:" | sed 's/^/  /'
    fi
    
    # Process log paths - they might contain variables
    if [ ! -z "$std_out" ]; then
        # Handle date variables in path
        std_out=$(echo "$std_out" | sed "s/%AUTORUN%/$(date +%Y%m%d)/g")
        std_out=$(echo "$std_out" | sed "s/%AUTODAY%/$(date +%d)/g")
        std_out=$(echo "$std_out" | sed "s/%AUTOMONTH%/$(date +%m)/g")
        std_out=$(echo "$std_out" | sed "s/%AUTOYEAR%/$(date +%Y)/g")
        
        # Handle job name variables
        std_out=$(echo "$std_out" | sed "s/%JOB%/${job_name}/g")
    fi
    
    if [ ! -z "$std_err" ]; then
        # Handle date variables in path
        std_err=$(echo "$std_err" | sed "s/%AUTORUN%/$(date +%Y%m%d)/g")
        std_err=$(echo "$std_err" | sed "s/%AUTODAY%/$(date +%d)/g")
        std_err=$(echo "$std_err" | sed "s/%AUTOMONTH%/$(date +%m)/g")
        std_err=$(echo "$std_err" | sed "s/%AUTOYEAR%/$(date +%Y)/g")
        
        # Handle job name variables
        std_err=$(echo "$std_err" | sed "s/%JOB%/${job_name}/g")
    fi
    
    # Display log file locations
    echo -e "${CYAN}Log File Locations:${NC}"
    echo -e "  ${MAGENTA}STDOUT:${NC} ${std_out:-Not defined}"
    echo -e "  ${MAGENTA}STDERR:${NC} ${std_err:-Not defined}"
    
    # Check if log files exist
    if [ ! -z "$std_out" ] && [ -f "$std_out" ]; then
        echo -e "${GREEN}STDOUT log file exists.${NC}"
    elif [ ! -z "$std_out" ]; then
        echo -e "${RED}Warning: STDOUT log file does not exist at the specified location.${NC}"
    fi
    
    if [ ! -z "$std_err" ] && [ -f "$std_err" ]; then
        echo -e "${GREEN}STDERR log file exists.${NC}"
    elif [ ! -z "$std_err" ]; then
        echo -e "${RED}Warning: STDERR log file does not exist at the specified location.${NC}"
    fi
    
    # Return the log paths as a colon-separated string
    echo "${std_out}:${std_err}"
}

# Function to display logs
display_logs() {
    local log_paths=$1
    local log_type=$2
    
    # Split the log paths
    IFS=':' read -r stdout_path stderr_path <<< "$log_paths"
    
    # Define which log to display
    local log_path=""
    local log_name=""
    
    if [ "$log_type" == "stdout" ] && [ ! -z "$stdout_path" ]; then
        log_path="$stdout_path"
        log_name="STDOUT"
    elif [ "$log_type" == "stderr" ] && [ ! -z "$stderr_path" ]; then
        log_path="$stderr_path"
        log_name="STDERR"
    elif [ "$log_type" == "both" ]; then
        # Display both logs
        if [ ! -z "$stdout_path" ] && [ -f "$stdout_path" ]; then
            echo -e "\n${CYAN}================== STDOUT LOG ==================${NC}"
            echo -e "${YELLOW}File: ${stdout_path}${NC}"
            echo -e "${CYAN}===============================================${NC}\n"
            cat "$stdout_path"
        fi
        
        if [ ! -z "$stderr_path" ] && [ -f "$stderr_path" ]; then
            echo -e "\n${CYAN}================== STDERR LOG ==================${NC}"
            echo -e "${YELLOW}File: ${stderr_path}${NC}"
            echo -e "${CYAN}===============================================${NC}\n"
            cat "$stderr_path"
        fi
        
        return 0
    fi
    
    # Check if log file exists
    if [ -z "$log_path" ]; then
        echo -e "${RED}Error: ${log_name} log file path is not defined for this job.${NC}"
        return 1
    fi
    
    if [ ! -f "$log_path" ]; then
        echo -e "${RED}Error: ${log_name} log file does not exist at: ${log_path}${NC}"
        return 1
    fi
    
    # Display the log
    echo -e "\n${CYAN}================== ${log_name} LOG ==================${NC}"
    echo -e "${YELLOW}File: ${log_path}${NC}"
    echo -e "${CYAN}===============================================${NC}\n"
    cat "$log_path"
    
    return 0
}

# Function to get job run history
get_job_runs() {
    local job_name=$1
    local show_all=$2
    
    echo -e "${BLUE}Retrieving job run history for '${job_name}'...${NC}"
    
    # Use autosyslog to get job history
    local job_history=""
    
    if [ "$show_all" == "true" ]; then
        job_history=$(autosyslog -j "${job_name}" -r)
    else
        # Get only the last run
        job_history=$(autosyslog -j "${job_name}" -r | tail -20)
    fi
    
    # Display job history
    echo -e "${CYAN}Job Run History:${NC}"
    echo "$job_history" | grep -E "RUN|STARTING|RUNNING|SUCCESS|FAILURE|TERMINATED" | sed 's/^/  /'
    
    return 0
}

# Main function
main() {
    local job_name=""
    local run_num="last"
    local show_all=false
    
    # Show banner
    show_banner
    
    # Parse command line arguments
    while getopts "j:r:ah" opt; do
        case ${opt} in
            j)
                job_name="${OPTARG}"
                ;;
            r)
                run_num="${OPTARG}"
                ;;
            a)
                show_all=true
                ;;
            h)
                usage
                exit 0
                ;;
            \?)
                echo -e "${RED}Invalid option: -${OPTARG}${NC}" 1>&2
                usage
                exit 1
                ;;
        esac
    done
    
    # Check if autosys is available
    check_autosys
    
    # If job name is not provided, prompt for it
    if [ -z "$job_name" ]; then
        echo -e "${YELLOW}Please enter the AutoSys job name:${NC}"
        read -r job_name
        
        if [ -z "$job_name" ]; then
            echo -e "${RED}Error: Job name cannot be empty.${NC}"
            exit 1
        fi
    fi
    
    # Validate job exists
    validate_job "$job_name"
    
    # Get job details
    get_job_details "$job_name"
    
    # Get job run history
    get_job_runs "$job_name" "$show_all"
    
    # Get log locations
    log_paths=$(get_log_locations "$job_name" "$run_num")
    
    # Menu for log display options
    echo -e "\n${YELLOW}What would you like to do?${NC}"
    echo -e "  ${CYAN}1)${NC} View STDOUT log"
    echo -e "  ${CYAN}2)${NC} View STDERR log"
    echo -e "  ${CYAN}3)${NC} View both logs"
    echo -e "  ${CYAN}4)${NC} Exit"
    
    read -p "Enter your choice (1-4): " choice
    
    case $choice in
        1)
            display_logs "$log_paths" "stdout"
            ;;
        2)
            display_logs "$log_paths" "stderr"
            ;;
        3)
            display_logs "$log_paths" "both"
            ;;
        4)
            echo -e "${GREEN}Exiting...${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}Invalid choice.${NC}"
            exit 1
            ;;
    esac
    
    # Exit with success
    echo -e "\n${GREEN}Log retrieval complete.${NC}"
    exit 0
}

# Execute main function
main "$@"
