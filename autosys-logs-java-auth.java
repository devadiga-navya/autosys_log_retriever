import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AutosysLogRetriever - A standalone Java program to retrieve stdout and stderr logs for Autosys jobs
 * 
 * This program uses Autosys command-line utilities with explicit authentication to fetch 
 * information about a job and retrieve its most recent stdout and stderr logs.
 */
public class AutosysLogRetriever {

    // Regex patterns to extract data from autorep output
    private static final Pattern STATUS_PATTERN = Pattern.compile("Status/Event:\\s+(\\w+)");
    private static final Pattern LAST_RUN_PATTERN = Pattern.compile("Last Run:\\s+([\\d/]+\\s+[\\d:]+)");
    private static final Pattern STD_OUT_FILE_PATTERN = Pattern.compile("std_out_file:\\s*(.*?)(?:\\s+|$)");
    private static final Pattern STD_ERR_FILE_PATTERN = Pattern.compile("std_err_file:\\s*(.*?)(?:\\s+|$)");
    private static final Pattern JOB_DIR_PATTERN = Pattern.compile("job_dir:\\s*(.*?)(?:\\s+|$)");

    // Authentication information
    private String username;
    private String password;
    private String instance;
    private String server;

    /**
     * Constructor with authentication parameters
     * 
     * @param username Autosys username
     * @param password Autosys password
     * @param instance Autosys instance name
     * @param server   Autosys server (optional)
     */
    public AutosysLogRetriever(String username, String password, String instance, String server) {
        this.username = username;
        this.password = password;
        this.instance = instance;
        this.server = server;
    }

    /**
     * Main method
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        // Parse command line arguments
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String jobName = null;
        String username = null;
        String password = null;
        String instance = null;
        String server = null;

        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-j":
                case "--job":
                    if (i + 1 < args.length) jobName = args[++i];
                    break;
                case "-u":
                case "--user":
                    if (i + 1 < args.length) username = args[++i];
                    break;
                case "-p":
                case "--password":
                    if (i + 1 < args.length) password = args[++i];
                    break;
                case "-i":
                case "--instance":
                    if (i + 1 < args.length) instance = args[++i];
                    break;
                case "-s":
                case "--server":
                    if (i + 1 < args.length) server = args[++i];
                    break;
                case "-h":
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
                default:
                    // If no flag is specified and jobName is not set, assume it's the job name
                    if (jobName == null && !args[i].startsWith("-")) {
                        jobName = args[i];
                    }
                    break;
            }
        }

        // Check required parameters
        if (jobName == null) {
            System.err.println("Error: Job name is required");
            printUsage();
            System.exit(1);
        }

        // If username is provided but password isn't, prompt for password
        if (username != null && password == null) {
            Console console = System.console();
            if (console != null) {
                char[] passwordChars = console.readPassword("Enter password for %s: ", username);
                password = new String(passwordChars);
                // Clear the password from memory
                java.util.Arrays.fill(passwordChars, ' ');
            } else {
                System.err.println("Console not available. Please provide password with -p option.");
                System.exit(1);
            }
        }

        // If instance is not provided but username is, prompt for instance
        if (username != null && instance == null) {
            Console console = System.console();
            if (console != null) {
                instance = console.readLine("Enter Autosys instance name: ");
            } else {
                System.err.println("Console not available. Please provide instance with -i option.");
                System.exit(1);
            }
        }

        System.out.println("Retrieving logs for Autosys job: " + jobName);
        
        try {
            AutosysLogRetriever retriever;
            
            if (username != null && password != null && instance != null) {
                retriever = new AutosysLogRetriever(username, password, instance, server);
            } else {
                // Use environment if no credentials are provided
                retriever = new AutosysLogRetriever(null, null, null, null);
            }
            
            // Get job details
            Map<String, String> jobDetails = retriever.getJobDetails(jobName);
            if (jobDetails == null || jobDetails.isEmpty()) {
                System.err.println("Failed to retrieve job details.");
                System.exit(1);
            }

            // Get and display logs
            retriever.getJobLogs(jobDetails);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Print usage information
     */
    private static void printUsage() {
        System.out.println("Usage: java AutosysLogRetriever [options] <job_name>");
        System.out.println("  or:  java AutosysLogRetriever [options] -j <job_name>");
        System.out.println("\nOptions:");
        System.out.println("  -j, --job <job_name>      Name of the Autosys job");
        System.out.println("  -u, --user <username>     Autosys username");
        System.out.println("  -p, --password <password> Autosys password");
        System.out.println("  -i, --instance <instance> Autosys instance name");
        System.out.println("  -s, --server <server>     Autosys server (optional)");
        System.out.println("  -h, --help                Show this help message");
        System.out.println("\nIf username, password, or instance are not provided, the script will use");
        System.out.println("the current environment settings or prompt for credentials.");
    }

    /**
     * Retrieves details about an Autosys job using the autorep command
     * 
     * @param jobName Name of the Autosys job
     * @return Map containing job details
     */
    private Map<String, String> getJobDetails(String jobName) throws IOException, InterruptedException {
        // Build command with authentication if provided
        List<String> command = new ArrayList<>();
        command.add("autorep");
        command.add("-j");
        command.add(jobName);
        command.add("-L");
        
        // Add authentication if provided
        if (username != null && password != null) {
            command.add("-u");
            command.add(username);
            
            // Note: Passing password on command line is not secure
            // This is only for demonstration - in production, use environment variables
            // or a secure credential store
            command.add("-p");
            command.add(password);
        }
        
        if (instance != null) {
            command.add("-i");
            command.add(instance);
        }
        
        if (server != null) {
            command.add("-s");
            command.add(server);
        }

        // Create process to run autorep command
        ProcessBuilder processBuilder = new ProcessBuilder(command);
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
    private void getJobLogs(Map<String, String> jobDetails) throws IOException, InterruptedException {
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
    private void displayFileContents(Path path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }
    }

    /**
     * Retrieves logs using Autosys utilities with authentication
     * 
     * @param jobName Name of the job
     * @param isStdOut True for stdout, false for stderr
     */
    private void retrieveLogViaAutosys(String jobName, boolean isStdOut) throws IOException, InterruptedException {
        // Build command with authentication if provided
        List<String> command = new ArrayList<>();
        command.add("autosyslog");
        command.add("-j");
        command.add(jobName);
        command.add(isStdOut ? "-o" : "-e");
        
        // Add authentication if provided
        if (username != null && password != null) {
            command.add("-u");
            command.add(username);
            command.add("-p");
            command.add(password);
        }
        
        if (instance != null) {
            command.add("-i");
            command.add(instance);
        }
        
        if (server != null) {
            command.add("-s");
            command.add(server);
        }
        
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        
        if (exitCode != 0) {
            System.err.println("Error retrieving logs via autosyslog. Exit code: " + exitCode);
        }
    }
}
