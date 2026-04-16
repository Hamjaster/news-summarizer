package com.example.newssummarizer.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.newssummarizer.model.DateRange;
import com.example.newssummarizer.model.NewsArticle;
import com.example.newssummarizer.model.NewsQuery;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LlmCall {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String HARDCODED_GEMINI_API_KEY = "AQ.Ab8RN6LvSOlrZftFazxksqcsT-UeqBbjB52yWX17E5sMCp4ygQ";
    private static final String HARDCODED_GEMINI_MODEL = "gemini-2.0-flash";

    private final HttpClient httpClient;
    private final String geminiApiKey;
    private final String geminiModel;

    public LlmCall() {
        this(HttpClient.newHttpClient(), HARDCODED_GEMINI_API_KEY, HARDCODED_GEMINI_MODEL);
    }

    LlmCall(HttpClient httpClient, String geminiApiKey, String geminiModel) {
        this.httpClient = httpClient;
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
    }

    // this should return a JSON that looks like this:
    // {
    // "searchText": "some search text",
    // "dateRange": {
    // "startDate": "2024-01-01",
    // "endDate": "2024-01-31"
    // }
    public NewsQuery extractQuery(String userQuery) {
        String normalized = userQuery == null ? "" : userQuery.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("User query cannot be empty.");
        }
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is missing in LlmCall.HARDCODED_GEMINI_API_KEY.");
        }
        if (geminiApiKey.contains("PASTE_YOUR_GEMINI_API_KEY_HERE")) {
            throw new IllegalStateException("Replace HARDCODED_GEMINI_API_KEY in LlmCall.java with your real key.");
        }

        LocalDate today = LocalDate.now();
        String prompt = """
                Convert the user request into JSON only. No markdown, no explanation.
                Return exactly this schema:
                {
                  "searchText": "string",
                  "dateRange": {
                    "startDate": "yyyy-MM-dd",
                    "endDate": "yyyy-MM-dd"
                  }
                }
                Rules:
                - searchText must be concise and suitable for a news API search query.
                - Resolve relative dates using today's date: %s
                - If no date is provided, use the last 30 days ending today.
                User request: %s
                """.formatted(today, normalized);

        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModel + ":generateContent?key=" + geminiApiKey;

        try {
            String requestBody = buildRequestBody(prompt);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini request failed with HTTP " + response.statusCode()
                        + ": " + response.body());
            }

            String modelText = extractModelText(response.body());
            String jsonText = stripCodeFences(modelText);
            JsonNode queryJson = MAPPER.readTree(jsonText);

            String searchText = queryJson.path("searchText").asText(normalized).trim();
            JsonNode dateRangeNode = queryJson.path("dateRange");

            LocalDate defaultEnd = today;
            LocalDate defaultStart = today.minusDays(30);

            LocalDate startDate = parseDateOrDefault(dateRangeNode.path("startDate").asText(null), defaultStart);
            LocalDate endDate = parseDateOrDefault(dateRangeNode.path("endDate").asText(null), defaultEnd);

            if (startDate.isAfter(endDate)) {
                LocalDate temp = startDate;
                startDate = endDate;
                endDate = temp;
            }

            return new NewsQuery(searchText, new DateRange(startDate, endDate));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini request was interrupted.", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Gemini response.", e);
        }
    }

    private String buildRequestBody(String prompt) throws IOException {
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        Map<String, Object> payload = new HashMap<>();
        payload.put("contents", List.of(content));

        return MAPPER.writeValueAsString(payload);
    }

    private String extractModelText(String responseBody) throws IOException {
        JsonNode root = MAPPER.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini returned no candidates: " + responseBody);
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new IllegalStateException("Gemini returned no content parts: " + responseBody);
        }

        String text = parts.get(0).path("text").asText(null);
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Gemini returned empty text response: " + responseBody);
        }

        return text;
    }

    private String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }

        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed.replace("```", "").trim();
        }

        String withoutFirstFence = trimmed.substring(firstNewline + 1);
        int lastFence = withoutFirstFence.lastIndexOf("```");
        if (lastFence >= 0) {
            return withoutFirstFence.substring(0, lastFence).trim();
        }

        return withoutFirstFence.trim();
    }

    private LocalDate parseDateOrDefault(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }

    // returns a summary string
    public String buildSummary(String userQuery, List<NewsArticle> articles) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}