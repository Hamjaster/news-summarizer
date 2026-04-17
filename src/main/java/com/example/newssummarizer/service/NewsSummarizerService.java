package com.example.newssummarizer.service;

import java.util.List;

import com.example.newssummarizer.api.ApiCall;
import com.example.newssummarizer.llm.LlmCall;
import com.example.newssummarizer.model.DateRange;
import com.example.newssummarizer.model.NewsArticle;
import com.example.newssummarizer.model.NewsQuery;

public class NewsSummarizerService {

    private final ApiCall apiCall;
    private final LlmCall llmCall;

    public NewsSummarizerService() {
        this.apiCall = new ApiCall();
        this.llmCall = new LlmCall();
    }

    /**
     * Run the end-to-end summarization flow for a raw user input.
     * Steps:
     *  1) extract query + date range from user input using the LLM
     *  2) fetch news using the ApiCall (uses searchText + dateRange)
     *  3) build a summary with the LLM and print it
     *
     * This method implements the second point requested: calling
     * ApiCall.fetchNews with the searchText and DateRange obtained
     * from the LLM-extracted NewsQuery.
     */
    public void run(String userInput) {
        // 1) extract query and date range
        NewsQuery newsQuery = llmCall.extractQuery(userInput);
        if (newsQuery == null) {
            System.out.println("Failed to extract query from input.");
            return;
        }

        String searchText = newsQuery.getSearchText();
        DateRange dateRange = newsQuery.getDateRange();

        // defensive checks
        if (searchText == null || searchText.isEmpty()) {
            System.out.println("No search text found in the extracted query.");
            return;
        }

        // 2) call the API to fetch news articles (this is the requested change)
        List<NewsArticle> articles = apiCall.fetchNews(searchText, dateRange);

        // 3) build summary using the LLM and print it
        String summary = llmCall.buildSummary(searchText, articles);
        System.out.println("--- Summary ---");
        System.out.println(summary);
    }
}