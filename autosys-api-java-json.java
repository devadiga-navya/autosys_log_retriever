import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Scanner;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * AutosysApiLogRetriever - A Java program to retrieve Autosys job logs using the REST API
 * and return them as a JSON object
 * 
 * This implementation makes direct API calls to the Autosys Web Services REST API
 * to retrieve job logs without relying on command-line utilities. It returns
 * the logs as a JSON object rather than displaying them.
 */
public class AutosysApiLogRetriever {

    private final String baseUrl;
    private final String username;
    private final String password;
    private final String instance;
    private final boolean ignoreSslErrors;

    /**
     * Constructor
     * 
     * @param server          Autosys API server host
     * @param port            Autosys API server port
     * @param username        Autosys username
     * @param password        Autosys password
     * @param instance        Autosys instance
     * @param ignoreSslErrors Whether to ignore SSL certificate errors
     */
    public AutosysApiLogRetriever(String server, int port, String username, String password, String instance,
            boolean ignoreSslErrors) {
        this.baseUrl = String.format("https://%s:%d/AEWS/rest", server, port);
        this.username = username;
        this.password = password;
        this.instance = instance;
        this.ignoreSslErrors = ignoreSslErrors;

        if (ignoreSslErrors) {
            disableSslVerification();
        }
    }

    /**
     * Main method to demonstrate usage
     * 
     * @param args Command line arguments (job name)
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java AutosysApiLogRetriever <job_name>");
            System.exit(1);
        }

        String jobName = args[0];
        
        // Interactive configuration
        Scanner scanner = new Scanner(System.in);
        
        System.out.print("Enter Autosys server hostname: ");
        String server = scanner.nextLine();
        
        System.out.print("Enter Autosys API port [8443]: ");
        String portStr = scanner.nextLine();
        int port = portStr.isEmpty() ? 8443 : Integer.parseInt(portStr);
        
        System.out.print("Enter Autosys username: ");
        String username = scanner.nextLine();
        
        System.out.print("Enter Autosys password: ");
        String password = scanner.nextLine();
        
        System.out.print("Enter Autosys instance: ");
        String instance = scanner.nextLine();
        
        System.out.print("Ignore SSL certificate errors? (y/n): ");
        boolean ignoreSslErrors = scanner.nextLine().toLowerCase().startsWith("y");
        
        scanner.close();

        try {
            AutosysApiLogRetriever retriever = new AutosysApiLogRetriever(server, port, username, password, instance, ignoreSslErrors);
            
            // Get logs for the job and print the JSON result
            JSONObject result = retriever.getJobLogs(jobName);
            System.out.println(result.toString(2)); // Pretty print with 2-space indentation
        } catch (Exception e) {
            System.err.println("Error retrieving logs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get logs for a specified job
     * 
     * @param jobName Name of the Autosys job
     * @return JSONObject containing job details and logs
     */
    public JSONObject getJobLogs(String jobName) {
        JSONObject result = new JSONObject();
        result.put("job_name", jobName);
        result.put("success", false);
        
        try {
            // Step 1: Get job details
            JSONObject jobDetails = getJobDetails(jobName);
            
            if (jobDetails == null) {
                result.put("error", "Failed to retrieve job details");
                return result;
            }
            
            // Extract relevant job details
            if (jobDetails.has("status")) {
                result.put("status", jobDetails.getString("status"));
            }
            
            // Step 2: Get job runs
            JSONArray jobRuns = getJobRuns(jobName);
            
            if (jobRuns == null || jobRuns.length() == 0) {
                result.put("error", "No runs found for job");
                return result;
            }
            
            // Step 3: Get the most recent run
            JSONObject lastRun = jobRuns.getJSONObject(0);
            int runId = lastRun.getInt("id");
            
            // Add run information
            JSONObject runInfo = new JSONObject();
            runInfo.put("id", runId);
            runInfo.put("status", lastRun.optString("status", "Unknown"));
            runInfo.put("start_time", lastRun.optString("startTime", "N/A"));
            runInfo.put("end_time", lastRun.optString("endTime", "N/A"));
            result.put("last_run", runInfo);
            
            // Step 4: Get logs for this run
            JSONObject logs = getJobRunLogs(jobName, runId);
            
            if (logs == null) {
                result.put("error", "Failed to retrieve logs for run: " + runId);
                return result;
            }
            
            // Extract stdout and stderr logs
            String stdoutLog = logs.optString("stdout", "");
            String stderrLog = logs.optString("stderr", "");
            
            // Add logs to result
            JSONObject logData = new JSONObject();
            logData.put("stdout", stdoutLog);
            logData.put("stderr", stderrLog);
            result.put("logs", logData);
            
            // Indicate success
            result.put("success", true);
            
        } catch (Exception e) {
            result.put("error", "Exception: " + e.getMessage());
            // You might want to log the exception stack trace here
        }
        
        return result;
    }

    /**
     * Get details for a specific job
     * 
     * @param jobName Name of the job
     * @return JSONObject containing job details
     * @throws IOException If an error occurs during the API call
     */
    private JSONObject getJobDetails(String jobName) throws IOException {
        String url = String.format("%s/job/%s/%s", baseUrl, instance, jobName);
        String response = makeApiCall(url);
        
        return response != null ? new JSONObject(response) : null;
    }

    /**
     * Get runs for a specific job
     * 
     * @param jobName Name of the job
     * @return JSONArray containing job runs
     * @throws IOException If an error occurs during the API call
     */
    private JSONArray getJobRuns(String jobName) throws IOException {
        String url = String.format("%s/job/%s/%s/runs", baseUrl, instance, jobName);
        String response = makeApiCall(url);
        
        return response != null ? new JSONArray(response) : null;
    }

    /**
     * Get logs for a specific job run
     * 
     * @param jobName Name of the job
     * @param runId   ID of the run
     * @return JSONObject containing log data
     * @throws IOException If an error occurs during the API call
     */
    private JSONObject getJobRunLogs(String jobName, int runId) throws IOException {
        String url = String.format("%s/job/%s/%s/runs/%d/logs", baseUrl, instance, jobName, runId);
        String response = makeApiCall(url);
        
        return response != null ? new JSONObject(response) : null;
    }

    /**
     * Make an API call to the specified URL
     * 
     * @param urlString URL to call
     * @return String containing the response
     * @throws IOException If an error occurs during the API call
     */
    private String makeApiCall(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        // Set up basic authentication
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + encodedAuth;
        
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", authHeader);
        connection.setRequestProperty("Accept", "application/json");
        
        int responseCode = connection.getResponseCode();
        
        if (responseCode != 200) {
            StringBuilder errorResponse = new StringBuilder();
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
            } catch (Exception e) {
                // Ignore errors reading the error stream
            }
            
            throw new IOException("API call failed with code " + responseCode + ": " + errorResponse.toString());
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        return response.toString();
    }
    
    /**
     * Disable SSL certificate validation
     * Warning: This should only be used in development or controlled environments
     */
    private void disableSslVerification() {
        try {
            // Create a trust manager that accepts all certificates
            TrustManager[] trustAllCerts = new TrustManager[] { 
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            
            // Create an SSL context with the trust manager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            
            // Set the default SSL socket factory
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            
            // Set hostname verifier to accept all hostnames
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to disable SSL verification: " + e.getMessage(), e);
        }
    }
}
