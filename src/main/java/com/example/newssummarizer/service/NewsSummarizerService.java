package com.example.newssummarizer.service;

import com.example.newssummarizer.api.ApiCall;
import com.example.newssummarizer.llm.LlmCall;
import com.example.newssummarizer.model.NewsQuery;

import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class NewsSummarizerService {

    private final ApiCall apiCall;
    private final LlmCall llmCall;

    public NewsSummarizerService() {
        this.apiCall = new ApiCall();
        this.llmCall = new LlmCall();
    }

    public NewsQuery runStep1ExtractQuery() {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your news request: ");
        String userInput = scanner.nextLine();

        NewsQuery query = llmCall.extractQuery(userInput);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        System.out.println("\nExtracted Query JSON (object form):");
        System.out.println("searchText: " + query.getSearchText());
        System.out.println("dateRange.startDate: " + query.getDateRange().getStartDate().format(formatter));
        System.out.println("dateRange.endDate: " + query.getDateRange().getEndDate().format(formatter));

        return query;
    }

    // 1)
    // take input from user here!
    // call llmCall.extractQuery to get the query and date range
    // NOW, we have the JSON :
    // {
    // "searchText": "some search text",
    // "dateRange": {
    // "startDate": "2024-01-01",
    // "endDate": "2024-01-31"
    // }

    // 2)
    // call apiCall.fetchNews with the searchText and dateRange to get the news
    // articles
    // NOW, we have NewsArticle[]

    // 3)
    // call LLMcall.buildSummary with passing the NewsArticle[] + the system prompt'
    // print that summary string on the termincal
}