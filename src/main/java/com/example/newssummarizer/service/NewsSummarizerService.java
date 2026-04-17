package com.example.newssummarizer.service;

import java.util.List;

import com.example.newssummarizer.api.ApiCall;
import com.example.newssummarizer.llm.LlmCall;
import com.example.newssummarizer.model.NewsQuery;

import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * Service layer that coordinates the full news summarization pipeline.
 *
 * File interaction flow:
 * - NewsSummarizerApplication -> calls this class.
 * - This class -> calls LlmCall for query extraction and summary generation.
 * - This class -> calls ApiCall to fetch news articles (Step 2, pending team
 * integration).
 */
public class NewsSummarizerService {

    private final ApiCall apiCall;
    private final LlmCall llmCall;

    public NewsSummarizerService() {
        this.apiCall = new ApiCall();
        this.llmCall = new LlmCall();
    }

    /**
     * Step 1:
     * - reads the user request from terminal,
     * - asks LLM to extract structured query and date range,
     * - prints extracted values.
     */
    public NewsQuery runStep1ExtractQuery() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your news request: ");
        String userInput = scanner.nextLine();

        // LlmCall converts free-text into NewsQuery(searchText + dateRange).
        NewsQuery query = llmCall.extractQuery(userInput);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        // Print structured output so user can verify extracted values.
        System.out.println("\nExtracted Query JSON (object form):");
        System.out.println("searchText: " + query.getSearchText());
        System.out.println("dateRange.startDate: " + query.getDateRange().getStartDate().format(formatter));
        System.out.println("dateRange.endDate: " + query.getDateRange().getEndDate().format(formatter));

        return query;
    }

    // Pipeline plan for final flow:
    // Step 1: runStep1ExtractQuery() -> gets NewsQuery from user text via LLM.
    // Step 2: apiCall.fetchNews(searchText, dateRange) -> returns NewsArticle list.
    // Step 3: llmCall.buildSummary(userQuery, articles) -> returns final summary
    // text.
}