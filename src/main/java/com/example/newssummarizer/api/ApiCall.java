package com.example.newssummarizer.api;

import com.example.newssummarizer.model.DateRange;
import com.example.newssummarizer.model.NewsArticle;

import java.util.List;

/**
 * API layer for external news provider integration.
 *
 * This class is responsible for converting internal query inputs
 * into external API calls and mapping results into NewsArticle objects.
 */
public class ApiCall {
    /**
     * Fetches news articles for the given search text and date range.
     *
     * @param query     free-text search query generated from user intent
     * @param dateRange start and end date limits for filtering articles
     * @return list of normalized NewsArticle objects
     */
    public List<NewsArticle> fetchNews(String query, DateRange dateRange) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}