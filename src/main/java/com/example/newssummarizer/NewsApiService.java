/*
 * This file fetches news articles from NewsAPI.
 * It builds the search URL, makes the request, and returns all article titles and descriptions as one combined text.
 * It keeps NewsAPI communication in one place.
 * It handles network and response errors gracefully.
 */
package com.example.newssummarizer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class NewsApiService {

    private static final String NEWS_API_KEY_ENV = "NEWS_API_KEY";
    private static final String NEWS_API_BASE = "https://newsapi.org/v2/everything";

    private final HttpClient httpClient;
    private final String newsApiKey;

    /**
     * Creates a reusable NewsAPI service with one shared HTTP client.
     *
     * @param none This constructor does not receive arguments.
     * @return A ready NewsApiService object.
     */
    public NewsApiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        String configuredNewsApiKey = System.getenv(NEWS_API_KEY_ENV);
        if (configuredNewsApiKey == null || configuredNewsApiKey.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: NEWS_API_KEY is missing.",
                    "Please check your .env file and set NEWS_API_KEY.",
                    "The application will now exit gracefully."
            );
            this.newsApiKey = "";
            System.exit(1);
            return;
        }

        this.newsApiKey = configuredNewsApiKey.trim();
    }

    /**
     * Builds a NewsAPI URL using the keywords and dates, fetches articles, extracts each article's title and description, combines them all into one text block, and returns it.
     *
     * @param keywords Search keywords created by Gemini.
     * @param fromDate Start date in yyyy-MM-dd format.
     * @param toDate End date in yyyy-MM-dd format.
     * @return Combined article text, a no-results message, or an empty string on error.
     */
    public String fetchArticles(String keywords, String fromDate, String toDate) {
        if (keywords == null || keywords.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: No keywords were provided to NewsAPI.",
                    "Please try your search again."
            );
            return "";
        }

        try {
            String encodedKeywords = URLEncoder.encode(keywords.trim(), StandardCharsets.UTF_8.toString());
            String requestUrl = NEWS_API_BASE
                    + "?q=" + encodedKeywords
                    + "&from=" + fromDate
                    + "&to=" + toDate
                    + "&sortBy=publishedAt"
                    + "&language=en"
                    + "&apiKey=" + newsApiKey;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .timeout(Duration.ofSeconds(45))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                printErrorBox(
                        "ERROR: NewsAPI request failed with status " + response.statusCode() + ".",
                        "Please try again in a moment."
                );
                return "";
            }

            String responseBody = response.body();
            if (responseBody == null || responseBody.trim().isEmpty()) {
                printErrorBox(
                        "ERROR: NewsAPI returned an empty response.",
                        "Please try again."
                );
                return "";
            }

            JSONObject root = new JSONObject(responseBody);
            JSONArray articles = root.optJSONArray("articles");
            if (articles == null || articles.length() == 0) {
                return "No articles found for this query and date range.";
            }

            StringBuilder combinedText = new StringBuilder();
            for (int index = 0; index < articles.length(); index++) {
                JSONObject article = articles.optJSONObject(index);
                if (article == null) {
                    continue;
                }

                String title = normalizeField(article.optString("title", ""));
                String description = normalizeField(article.optString("description", ""));

                if (title.isEmpty() && description.isEmpty()) {
                    continue;
                }

                combinedText.append("Title: ").append(title.isEmpty() ? "N/A" : title).append("\n");
                combinedText.append("Description: ").append(description.isEmpty() ? "N/A" : description).append("\n\n");
            }

            if (combinedText.length() == 0) {
                return "No articles found for this query and date range.";
            }

            return combinedText.toString().trim();
        } catch (HttpTimeoutException exception) {
            printErrorBox(
                    "ERROR: NewsAPI request timed out.",
                    "Please check your connection and try again."
            );
            return "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            printErrorBox(
                    "ERROR: NewsAPI request was interrupted.",
                    "Please try again."
            );
            return "";
        } catch (IOException exception) {
            printErrorBox(
                    "ERROR: Could not reach NewsAPI.",
                    "Please check your internet and try again."
            );
            return "";
        } catch (Exception exception) {
            printErrorBox(
                    "ERROR: NewsAPI returned malformed data.",
                    "Please try again with another query."
            );
            return "";
        }
    }

    /**
     * Cleans one text field and removes placeholder null values.
     *
     * @param value The field value from NewsAPI.
     * @return A clean value or empty string.
     */
    private String normalizeField(String value) {
        if (value == null) {
            return "";
        }
        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty() || "null".equalsIgnoreCase(trimmedValue)) {
            return "";
        }
        return trimmedValue;
    }

    /**
     * Prints a clear bordered error message to the terminal.
     *
     * @param messageLines The error message lines to show.
     * @return Nothing. This method only prints output.
     */
    private void printErrorBox(String... messageLines) {
        int contentWidth = 62;
        for (String messageLine : messageLines) {
            if (messageLine != null && messageLine.length() > contentWidth) {
                contentWidth = messageLine.length();
            }
        }

        System.out.println("\u2554" + "\u2550".repeat(contentWidth + 2) + "\u2557");
        for (String messageLine : messageLines) {
            String safeMessageLine = messageLine == null ? "" : messageLine;
            System.out.println("\u2551 " + padRight(safeMessageLine, contentWidth) + " \u2551");
        }
        System.out.println("\u255A" + "\u2550".repeat(contentWidth + 2) + "\u255D");
    }

    /**
     * Pads text with spaces on the right for aligned box output.
     *
     * @param value The original text value.
     * @param width The desired line width.
     * @return A right-padded string.
     */
    private String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }
}