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

/**
 * Handles all Gemini LLM interactions for this project.
 *
 * Responsibilities:
 * - convert user query text into structured NewsQuery (Step 1)
 * - generate final human-readable summary from article list (Step 3)
 */
public class LlmCall {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // Keep this value private. Current setup intentionally uses a hardcoded key.
    private static final String HARDCODED_GEMINI_API_KEY = "AQ.Ab8RN6LvSOlrZftFazxksqcsT-UeqBbjB52yWX17E5sMCp4ygQ";
    // Default model used for both extraction and summarization.
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

    /**
     * Step 1 LLM call:
     * Converts free-text user request into NewsQuery with fields:
     * - searchText
     * - dateRange.startDate
     * - dateRange.endDate
     */
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
        // Gemini request structure: contents -> parts -> text
        Map<String, Object> part = new HashMap<>();
        part.put("text", prompt);

        Map<String, Object> content = new HashMap<>();
        content.put("parts", List.of(part));

        Map<String, Object> payload = new HashMap<>();
        payload.put("contents", List.of(content));

        return MAPPER.writeValueAsString(payload);
    }

    private String extractModelText(String responseBody) throws IOException {
        // Reads first candidate text from Gemini response JSON.
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
        // Some model responses include ```json ... ``` wrappers; remove them safely.
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
        // Uses fallback when model returns missing or invalid date text.
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }

    /**
     * Step 3 LLM call:
     * Builds a concise summary from the user's intent and fetched articles.
     */
    public String buildSummary(String userQuery, List<NewsArticle> articles) {
        String normalizedQuery = userQuery == null ? "" : userQuery.trim();
        if (normalizedQuery.isEmpty()) {
            throw new IllegalArgumentException("User query cannot be empty.");
        }
        if (articles == null || articles.isEmpty()) {
            return "No news articles were found for the selected query/date range, so there is nothing to summarize.";
        }
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is missing in LlmCall.HARDCODED_GEMINI_API_KEY.");
        }

        String articlesContext = buildArticlesContext(articles);
        String prompt = """
                You are a news summarizer assistant.
                Create a concise, factual summary for the user request below.

                User request:
                %s

                News articles:
                %s

                Output format:
                - First line: one-sentence overall summary.
                - Then 3 to 6 bullet points with key developments.
                - End with "Sources:" and include source names only (comma-separated).
                Rules:
                - Use only the provided articles.
                - If articles conflict, mention uncertainty briefly.
                - Keep it under 220 words.
                """.formatted(normalizedQuery, articlesContext);

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

            String summary = extractModelText(response.body());
            return stripCodeFences(summary);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gemini request was interrupted.", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse Gemini response.", e);
        }
    }

    private String buildArticlesContext(List<NewsArticle> articles) {
        // Limit number of articles to keep prompt size manageable.
        int maxArticles = Math.min(articles.size(), 15);
        StringBuilder context = new StringBuilder();

        for (int i = 0; i < maxArticles; i++) {
            NewsArticle article = articles.get(i);
            String title = safeValue(article.getTitle());
            String description = safeValue(article.getDescription());
            String source = safeValue(article.getSource());
            String url = safeValue(article.getUrl());

            context.append(i + 1).append(") ")
                    .append("Title: ").append(title).append('\n')
                    .append("Description: ").append(description).append('\n')
                    .append("Source: ").append(source).append('\n')
                    .append("URL: ").append(url).append("\n\n");
        }

        return context.toString().trim();
    }

    private String safeValue(String value) {
        // Prevent null/blank values from degrading summary prompt quality.
        if (value == null || value.isBlank()) {
            return "N/A";
        }
        return value.trim();
    }
}