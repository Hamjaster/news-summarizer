package com.example.newssummarizer;

import com.example.newssummarizer.service.NewsSummarizerService;

public class NewsSummarizerApplication {

    public static void main(String[] args) {
        NewsSummarizerService service = new NewsSummarizerService();
        service.runStep1ExtractQuery();
    }
}