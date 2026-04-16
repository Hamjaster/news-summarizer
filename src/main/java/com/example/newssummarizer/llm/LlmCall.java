package com.example.newssummarizer.llm;

import com.example.newssummarizer.model.NewsArticle;
import com.example.newssummarizer.model.NewsQuery;

import java.util.List;

public class LlmCall {
    // this should return a JSON that looks like this:
    // {
    //     "searchText": "some search text",    
    //     "dateRange": {
    //         "startDate": "2024-01-01",
    //         "endDate": "2024-01-31"
    //     }
    public NewsQuery extractQuery(String userQuery) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
    // returns a summary string 
    public String buildSummary(String userQuery, List<NewsArticle> articles) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}