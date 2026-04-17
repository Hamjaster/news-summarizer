package com.example.newssummarizer;

import com.example.newssummarizer.service.NewsSummarizerService;

/**
 * Entry point of the console application.
 *
 * Program flow (high-level):
 * 1) This class creates NewsSummarizerService.
 * 2) Service takes user input from terminal.
 * 3) Service calls LlmCall.extractQuery(...) to convert user text into
 * NewsQuery.
 * 4) Next planned steps are:
 * - ApiCall.fetchNews(...) to get articles
 * - LlmCall.buildSummary(...) to create final summary
 */
public class NewsSummarizerApplication {

    public static void main(String[] args) {
        // Start the orchestrator/service layer for the app flow.
        NewsSummarizerService service = new NewsSummarizerService();

        // Current implemented step: user input -> extracted searchText/dateRange.
        service.runStep1ExtractQuery();

        // Use provided CLI args as the user input for quick demos, otherwise use a default sample query.
        String userInput;
        if (args != null && args.length > 0) {
            userInput = String.join(" ", args);
        } else {
            userInput = "latest technology regulatory changes";
        }

        System.out.println("Running demo summarizer for input: '" + userInput + "'\n");
        service.run(userInput);
    }
}