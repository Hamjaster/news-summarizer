package com.example.newssummarizer.model;

public class NewsQuery {

    private String searchText;
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