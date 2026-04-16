package com.example.newssummarizer;

import com.example.newssummarizer.service.NewsSummarizerService;

public class NewsSummarizerApplication {

    public static void main(String[] args) {
        NewsSummarizerService service = new NewsSummarizerService();

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