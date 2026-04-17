package com.example.newssummarizer.api;

import com.example.newssummarizer.model.DateRange;
import com.example.newssummarizer.model.NewsArticle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * API layer for external news provider integration.
 *
 * This class is responsible for converting internal query inputs
 * into external API calls and mapping results into NewsArticle objects.
 */
public class ApiCall {
    // Calls the NewsAPI (https://newsapi.org) `everything` endpoint and maps the
    // response
    // to a List<NewsArticle>. The API key must be provided via the NEWS_API_KEY
    // environment variable.
    public List<NewsArticle> fetchNews(String query, DateRange dateRange) {
        List<NewsArticle> articles = new ArrayList<>();

        String apiKey = System.getenv("NEWS_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "NEWS_API_KEY environment variable is not set. Please set it to your NewsAPI key.");
        }

        try {
            StringBuilder sb = new StringBuilder("https://newsapi.org/v2/everything?");
            sb.append("q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));

            if (dateRange != null) {
                if (dateRange.getStartDate() != null) {
                    sb.append("&from=")
                            .append(URLEncoder.encode(dateRange.getStartDate().toString(), StandardCharsets.UTF_8));
                }
                if (dateRange.getEndDate() != null) {
                    sb.append("&to=")
                            .append(URLEncoder.encode(dateRange.getEndDate().toString(), StandardCharsets.UTF_8));
                }
            }

            sb.append("&sortBy=publishedAt");
            sb.append("&pageSize=20");
            sb.append("&apiKey=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));

            String url = sb.toString();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("News API returned status " + response.statusCode() + ": " + response.body());
                return articles;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode arr = root.get("articles");
            if (arr != null && arr.isArray()) {
                for (JsonNode node : arr) {
                    String title = node.hasNonNull("title") ? node.get("title").asText() : null;
                    String description = node.hasNonNull("description") ? node.get("description").asText() : null;
                    String urlStr = node.hasNonNull("url") ? node.get("url").asText() : null;
                    String content = node.hasNonNull("content") ? node.get("content").asText() : null;
                    String source = null;
                    JsonNode sourceNode = node.get("source");
                    if (sourceNode != null && sourceNode.hasNonNull("name")) {
                        source = sourceNode.get("name").asText();
                    }

                    NewsArticle a = new NewsArticle(title, description, source, urlStr);
                    a.setContent(content);
                    articles.add(a);
                }
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Error fetching news: " + e.getMessage());
            Thread.currentThread().interrupt();
        }

        return articles;
    }
}