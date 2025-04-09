import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AutosysLogRetriever - A standalone Java program to retrieve stdout and stderr logs for Autosys jobs
 * 
 * This program uses Autosys command-line utilities to fetch information about a job
 * and retrieve its most recent stdout and stderr logs.
 */
public class AutosysLogRetriever {

    // Regex patterns to extract data from autorep output
    private static final Pattern STATUS_PATTERN = Pattern.compile("Status/Event:\\s+(\\w+)");
    private static final Pattern LAST_RUN_PATTERN = Pattern.compile("Last Run:\\s+([\\d/]+\\s+[\\d:]+)");
    private static final Pattern STD_OUT_FILE_PATTERN = Pattern.compile("std_out_file:\\s*(.*?)(?:\\s+|$)");
    private static final Pattern STD_ERR_FILE_PATTERN = Pattern.compile("std_err_file:\\s*(.*?)(?:\\s+|$)");
    private static final Pattern JOB_DIR_PATTERN = Pattern.compile("job_dir:\\s*(.*?)(?:\\s+|$)");

    /**
     * Main method
     * 
     * @param args Command line arguments. Expected: job name
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java AutosysLogRetriever <job_name>");
            System.exit(1);
        }

        String jobName = args[0];
        System.out.println("Retrieving logs for Autosys job: " + jobName);

        try {
            // Get job details
            Map<String, String> jobDetails = getJobDetails(jobName);
            if (jobDetails == null || jobDetails.isEmpty()) {
                System.err.println("Failed to retrieve job details.");
                System.exit(1);
            }

            // Get and display logs
            getJobLogs(jobDetails);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Retrieves details about an Autosys job using the autorep command
     * 
     * @param jobName Name of the Autosys job
     * @return Map containing job details
     */
    private static Map<String, String> getJobDetails(String jobName) throws IOException, InterruptedException {
        // Create process to run autorep command
        ProcessBuilder processBuilder = new ProcessBuilder("autorep", "-j", jobName, "-L");
        processBuilder.redirectErrorStream(false);
        Process process = processBuilder.start();

        // Read command output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Check for errors
        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }
        }

        // Wait for process to complete
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("Error running autorep command. Exit code: " + exitCode);
            System.err.println("Error output: " + errorOutput.toString());
            return null;
        }

        // Parse output to extract job details
        String outputStr = output.toString();
        Map<String, String> jobDetails = new HashMap<>();
        jobDetails.put("job_name", jobName);

        // Extract status
        Matcher statusMatcher = STATUS_PATTERN.matcher(outputStr);
        if (statusMatcher.find()) {
            jobDetails.put("status", statusMatcher.group(1));
        }

        // Extract last run time
        Matcher lastRunMatcher = LAST_RUN_PATTERN.matcher(outputStr);
        if (lastRunMatcher.find()) {
            jobDetails.put("last_run", lastRunMatcher.group(1));
        }

        // Extract stdout file path
        Matcher stdOutMatcher = STD_OUT_FILE_PATTERN.matcher(outputStr);
        if (stdOutMatcher.find() && !stdOutMatcher.group(1).trim().isEmpty()) {
            jobDetails.put("std_out_file", stdOutMatcher.group(1).trim());
        }

        // Extract stderr file path
        Matcher stdErrMatcher = STD_ERR_FILE_PATTERN.matcher(outputStr);
        if (stdErrMatcher.find() && !stdErrMatcher.group(1).trim().isEmpty()) {
            jobDetails.put("std_err_file", stdErrMatcher.group(1).trim());
        }

        // If stdout/stderr files not found, try to construct paths based on job directory
        if (!jobDetails.containsKey("std_out_file") || !jobDetails.containsKey("std_err_file")) {
            Matcher jobDirMatcher = JOB_DIR_PATTERN.matcher(outputStr);
            if (jobDirMatcher.find()) {
                String jobDir = jobDirMatcher.group(1).trim();
                
                if (!jobDetails.containsKey("std_out_file")) {
                    jobDetails.put("std_out_file", jobDir + File.separator + jobName + ".out");
                }
                
                if (!jobDetails.containsKey("std_err_file")) {
                    jobDetails.put("std_err_file", jobDir + File.separator + jobName + ".err");
                }
            }
        }

        return jobDetails;
    }

    /**
     * Retrieves and displays logs for a job based on job details
     * 
     * @param jobDetails Map containing job details
     */
    private static void getJobLogs(Map<String, String> jobDetails) throws IOException, InterruptedException {
        // Print job information header
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Job Name: " + jobDetails.get("job_name"));
        System.out.println("Status: " + jobDetails.getOrDefault("status", "Unknown"));
        System.out.println("Last Run: " + jobDetails.getOrDefault("last_run", "Unknown"));
        System.out.println("=".repeat(80) + "\n");

        // Handle stdout file
        String stdOutFile = jobDetails.get("std_out_file");
        if (stdOutFile != null && !stdOutFile.isEmpty()) {
            System.out.println("\nSTDOUT LOG (" + stdOutFile + "):");
            System.out.println("-".repeat(80));
            
            try {
                // Try to read the file directly
                Path stdOutPath = Paths.get(stdOutFile);
                if (Files.exists(stdOutPath)) {
                    displayFileContents(stdOutPath);
                } else {
                    // If direct access fails, try to retrieve via Autosys utilities
                    System.out.println("Direct access to stdout file failed. Attempting to retrieve via Autosys utilities...");
                    retrieveLogViaAutosys(jobDetails.get("job_name"), true);
                }
            } catch (Exception e) {
                System.err.println("Error accessing stdout log: " + e.getMessage());
            }
        } else {
            System.out.println("\nSTDOUT LOG: Not available");
        }

        // Handle stderr file
        String stdErrFile = jobDetails.get("std_err_file");
        if (stdErrFile != null && !stdErrFile.isEmpty()) {
            System.out.println("\nSTDERR LOG (" + stdErrFile + "):");
            System.out.println("-".repeat(80));
            
            try {
                // Try to read the file directly
                Path stdErrPath = Paths.get(stdErrFile);
                if (Files.exists(stdErrPath)) {
                    displayFileContents(stdErrPath);
                } else {
                    // If direct access fails, try to retrieve via Autosys utilities
                    System.out.println("Direct access to stderr file failed. Attempting to retrieve via Autosys utilities...");
                    retrieveLogViaAutosys(jobDetails.get("job_name"), false);
                }
            } catch (Exception e) {
                System.err.println("Error accessing stderr log: " + e.getMessage());
            }
        } else {
            System.out.println("\nSTDERR LOG: Not available");
        }
    }

    /**
     * Displays the contents of a file
     * 
     * @param path Path to the file
     */
    private static void displayFileContents(Path path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }

    /**
     * Retrieves logs using Autosys utilities
     * 
     * @param jobName Name of the job
     * @param isStdOut True for stdout, false for stderr
     */
    private static void retrieveLogViaAutosys(String jobName, boolean isStdOut) throws IOException, InterruptedException {
        String option = isStdOut ? "-o" : "-e";
        
        ProcessBuilder processBuilder = new ProcessBuilder("autosyslog", "-j", jobName, option);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            System.err.println("Error retrieving logs via autosyslog. Exit code: " + exitCode);
        }
    }
}
