/*
 * This file talks to Gemini and improves raw user queries.
 * It turns everyday text into a short query the summarizer endpoint can use.
 * It handles API setup, request building, and response parsing safely.
 * It returns clear errors when Gemini cannot be reached.
 */
package com.example.newssummarizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class GeminiService {

    // Put your real Gemini key in this environment variable before running the app.
    private static final String GEMINI_API_KEY_ENV = "GEMINI_API_KEY";
    // Optional environment variable to override the model name.
    private static final String GEMINI_MODEL_ENV = "GEMINI_MODEL";
    // Default Gemini model used when GEMINI_MODEL is not provided.
    private static final String DEFAULT_GEMINI_MODEL = "gemini-2.0-flash";
    // Official Gemini endpoint base URL.
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a GeminiService with reusable HTTP and JSON helpers.
        * Parameters: none.
        * Returns: a ready service object for Gemini requests.
     */
    public GeminiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Sends raw user text to Gemini and returns a short endpoint-ready query.
     *
     * @param rawQuery Original user query from the terminal.
     * @return Formatted query text ready for the summarizer endpoint.
     */
    public String formatQueryWithGemini(String rawQuery) throws IOException {
        if (rawQuery == null || rawQuery.isBlank()) {
            throw new IOException("Error: Query is empty. Please type a search query and try again.");
        }

        String apiKey = System.getenv(GEMINI_API_KEY_ENV);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("Error: Gemini API key is missing. Set GEMINI_API_KEY and try again.");
        }

        String safeQuery = rawQuery.trim();
        String endpointUrl = buildGeminiEndpoint(apiKey.trim(), getModelName());

        try {
            String requestBody = buildRequestBody(safeQuery);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorBody;
                try (InputStream responseStream = response.body()) {
                    errorBody = readResponseBody(responseStream);
                }
                throw new IOException("Error: Gemini API returned HTTP " + response.statusCode()
                        + ". Details: " + shortenForMessage(errorBody));
            }

            String formattedQuery;
            try (InputStream responseStream = response.body()) {
                formattedQuery = extractFormattedQuery(responseStream, safeQuery);
            }
            if (formattedQuery.isBlank()) {
                throw new IOException("Error: Gemini API returned an empty formatted query. Please try again.");
            }

            return formattedQuery;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Error: Gemini request was interrupted. Please try again.", exception);
        } catch (IOException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Error:")) {
                throw exception;
            }
            throw new IOException("Error: Could not reach Gemini API. Please check your connection and try again.", exception);
        } catch (RuntimeException exception) {
            throw new IOException("Error: Gemini API response could not be read. Please try again.", exception);
        }
    }

    /**
     * Chooses the model name from environment settings or default value.
        * Parameters: none.
        * Returns: the Gemini model name to use for requests.
     */
    private String getModelName() {
        String configuredModel = System.getenv(GEMINI_MODEL_ENV);
        if (configuredModel == null || configuredModel.isBlank()) {
            return DEFAULT_GEMINI_MODEL;
        }
        return configuredModel.trim();
    }

    /**
     * Builds the full Gemini endpoint URL including model and API key.
     *
     * @param apiKey Valid API key used for Gemini authentication.
     * @param modelName Gemini model that will process the request.
     * @return Full endpoint URL string.
     */
    private String buildGeminiEndpoint(String apiKey, String modelName) {
        String encodedModel = URLEncoder.encode(modelName, StandardCharsets.UTF_8);
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        return GEMINI_BASE_URL + "/" + encodedModel + ":generateContent?key=" + encodedKey;
    }

    /**
     * Builds Gemini JSON payload that asks for a compact endpoint-ready query.
     *
     * @param rawQuery Raw terminal query entered by the user.
     * @return JSON request body string.
     */
    private String buildRequestBody(String rawQuery) throws IOException {
        // Gemini transformation: convert human text into one concise search phrase for an endpoint.
        String prompt = "Convert the user request into one concise search query for a summarizer API. "
                + "Return only plain text with no quotes and no markdown. User request: "
                + rawQuery;

        Map<String, Object> payload = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{Map.of("text", prompt)})
                }
        );

        return objectMapper.writeValueAsString(payload);
    }

    /**
     * Extracts and normalizes the best query string from Gemini API response JSON.
     *
     * @param responseStream Raw Gemini response stream.
     * @param fallbackQuery Original user query used when Gemini returns no text.
     * @return Clean formatted query text.
     */
    private String extractFormattedQuery(InputStream responseStream, String fallbackQuery) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseStream);
        JsonNode candidatesNode = rootNode.path("candidates");
        if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
            return fallbackQuery;
        }

        JsonNode firstPartNode = candidatesNode.get(0).path("content").path("parts");
        if (!firstPartNode.isArray() || firstPartNode.isEmpty()) {
            return fallbackQuery;
        }

        String modelText = firstPartNode.get(0).path("text").asText("").trim();
        if (modelText.isBlank()) {
            return fallbackQuery;
        }

        return modelText.replace("\n", " ").replaceAll("\\s+", " ").trim();
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