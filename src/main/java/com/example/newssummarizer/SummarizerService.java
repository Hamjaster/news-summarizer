/*
 * This file calls your summarizer endpoint with a formatted query.
 * It sends the query over HTTP and reads the summary from the response.
 * It safely handles network failures and invalid API replies.
 * It returns clean summary text for terminal output.
 */
package com.example.newssummarizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class SummarizerService {

    // Put your custom summarizer endpoint URL in this environment variable before running.
    private static final String SUMMARIZER_API_URL_ENV = "SUMMARIZER_API_URL";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a SummarizerService with reusable HTTP and JSON helpers.
        * Parameters: none.
        * Returns: a ready service object for summarizer requests.
     */
    public SummarizerService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Calls the summarizer API with the formatted query and returns summary text.
     *
     * @param formattedQuery Query already cleaned by GeminiService.
     * @return Summary text from the summarizer API.
     */
    public String getSummary(String formattedQuery) throws IOException {
        if (formattedQuery == null || formattedQuery.isBlank()) {
            throw new IOException("Error: Formatted query is empty. Please try another search.");
        }

        String endpointUrl = System.getenv(SUMMARIZER_API_URL_ENV);
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IOException("Error: Summarizer endpoint is missing. Set SUMMARIZER_API_URL and try again.");
        }

        try {
            String requestBody = buildRequestBody(formattedQuery.trim());
            HttpRequest request = buildRequest(endpointUrl.trim(), requestBody);

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody;
                try (InputStream responseStream = response.body()) {
                    errorBody = readResponseBody(responseStream);
                }
                throw new IOException("Error: Summarizer API returned HTTP " + response.statusCode()
                        + ". Details: " + shortenForMessage(errorBody));
            }

            String summary;
            try (InputStream responseStream = response.body()) {
                summary = extractSummary(responseStream);
            }
            if (summary.isBlank()) {
                throw new IOException("Error: Summarizer API returned an empty summary. Please try again.");
            }

            return summary;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Error: Summarizer request was interrupted. Please try again.", exception);
        } catch (IOException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Error:")) {
                throw exception;
            }
            throw new IOException("Error: Could not reach summarizer API. Please check your connection and try again.", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Error: Summarizer API response was invalid. Please try again.", exception);
        }
    }

    /**
     * Builds the JSON body expected by the summarizer endpoint.
     *
     * @param formattedQuery Query text prepared by Gemini.
     * @return JSON request body string.
     */
    private String buildRequestBody(String formattedQuery) throws IOException {
        Map<String, String> payload = Map.of("query", formattedQuery);
        return objectMapper.writeValueAsString(payload);
    }

    /**
     * Builds an HTTP POST request to the summarizer endpoint URL.
     *
     * @param endpointUrl Full summarizer endpoint URL (for example, https://api.example.com/summarize).
     * @param requestBody JSON body containing the query field.
     * @return Configured HttpRequest ready to send.
     */
    private HttpRequest buildRequest(String endpointUrl, String requestBody) {
        // Expected response format is JSON with one of these fields: summary, data.summary, or result.summary.
        return HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * Reads summary text from API response JSON or plain text fallback.
     *
     * @param responseStream Raw response stream returned by the summarizer API.
     * @return Best available summary text.
     */
    private String extractSummary(InputStream responseStream) throws IOException {
        String apiResponse = readResponseBody(responseStream).trim();
        if (apiResponse.isBlank()) {
            return "";
        }

        try {
            JsonNode rootNode = objectMapper.readTree(apiResponse);
            String topLevelSummary = rootNode.path("summary").asText("").trim();
            if (!topLevelSummary.isBlank()) {
                return topLevelSummary;
            }

            String dataSummary = rootNode.path("data").path("summary").asText("").trim();
            if (!dataSummary.isBlank()) {
                return dataSummary;
            }

            String resultSummary = rootNode.path("result").path("summary").asText("").trim();
            if (!resultSummary.isBlank()) {
                return resultSummary;
            }

            String messageSummary = rootNode.path("message").asText("").trim();
            if (!messageSummary.isBlank()) {
                return messageSummary;
            }
        } catch (IOException ignoredException) {
            // Fallback below handles non-JSON responses.
        }

        return apiResponse.trim();
    }

    /**
     * Reads the full HTTP response stream as UTF-8 text.
     *
     * @param responseStream Open response stream from the API call.
     * @return Full response text.
     */
    private String readResponseBody(InputStream responseStream) throws IOException {
        byte[] responseBytes = responseStream.readAllBytes();
        if (responseBytes.length == 0) {
            return "";
        }
        return new String(responseBytes, StandardCharsets.UTF_8);
    }

    /**
     * Shortens large API error text so terminal output stays readable.
     *
     * @param message Original API message text.
     * @return Shortened text suitable for user-facing errors.
     */
    private String shortenForMessage(String message) {
        if (message == null || message.isBlank()) {
            return "No extra error details were returned.";
        }
        String cleaned = message.replace("\n", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 240) {
            return cleaned;
        }
        return cleaned.substring(0, 240) + "...";
    }
}