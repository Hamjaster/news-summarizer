package com.example.newssummarizer.model;

/**
 * Structured query produced by LLM from user input.
 *
 * Contains both search text and date filters so downstream API calls
 * can run without additional NLP parsing.
 */
public class NewsQuery {

    // Search phrase to send to the news provider API.
    private String searchText;

    // Date limits for filtering the requested news window.
    private DateRange dateRange;

    public NewsQuery() {
    }

    public NewsQuery(String searchText, DateRange dateRange) {
        this.searchText = searchText;
        this.dateRange = dateRange;
    }

    public String getSearchText() {
        return searchText;
    }

    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public DateRange getDateRange() {
        return dateRange;
    }

    public void setDateRange(DateRange dateRange) {
        this.dateRange = dateRange;
    }
}