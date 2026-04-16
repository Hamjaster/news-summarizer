package com.example.newssummarizer.llm;

import java.time.LocalDate;
import java.util.List;

import com.example.newssummarizer.model.DateRange;
import com.example.newssummarizer.model.NewsArticle;
import com.example.newssummarizer.model.NewsQuery;

public class LlmCall {
    // Lightweight local implementation for extracting a query and building a summary.
    // In production this would call an LLM service.
    // This method returns a NewsQuery built from the raw user input.
    public NewsQuery extractQuery(String userQuery) {
        if (userQuery == null) return null;

        String trimmed = userQuery.trim();
        if (trimmed.isEmpty()) return null;

        // For demo: treat the entire input as the search text and use a 7-day date range.
        NewsQuery nq = new NewsQuery();
        nq.setSearchText(trimmed);
        DateRange dr = new DateRange(LocalDate.now().minusDays(7), LocalDate.now());
        nq.setDateRange(dr);
        return nq;
    }

    // returns a summary string built from provided articles
    public String buildSummary(String userQuery, List<NewsArticle> articles) {
        if (userQuery == null) userQuery = "(no query)";
        if (articles == null || articles.isEmpty()) {
            return "No articles found for '" + userQuery + "'.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Summary for '").append(userQuery).append("':\n");
        sb.append("Found ").append(articles.size()).append(" articles.\n\n");

        int i = 1;
        for (NewsArticle a : articles) {
            sb.append(i++).append(". ").append(a.getTitle() == null ? "(no title)" : a.getTitle()).append('\n');
            if (a.getDescription() != null && !a.getDescription().isEmpty()) {
                sb.append("   ").append(a.getDescription()).append('\n');
            }
            sb.append("   Source: ").append(a.getSource() == null ? "unknown" : a.getSource()).append(" | ");
            sb.append("URL: ").append(a.getUrl() == null ? "n/a" : a.getUrl()).append('\n');
            sb.append('\n');
        }

        sb.append("End of summary.");
        return sb.toString();
    }
}