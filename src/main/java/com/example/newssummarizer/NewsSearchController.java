package com.example.newssummarizer;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class NewsSearchController {

    @FXML private TextField queryField;
    @FXML private TextArea resultArea;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label wordStatsLabel;

    private GeminiService geminiService;
    private NewsApiService newsApiService;

    public void setServices(GeminiService gemini, NewsApiService newsApi) {
        this.geminiService = gemini;
        this.newsApiService = newsApi;
    }

    @FXML
    private void onSearch() {
        String query = queryField.getText().trim();
        if (query.isEmpty()) return;

        setLoading(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                String keywords = geminiService.formatQueryForNews(query);
                String toDate   = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                String fromDate = LocalDate.now().minusDays(21).format(DateTimeFormatter.ISO_LOCAL_DATE);
                String articles = newsApiService.fetchArticles(keywords, fromDate, toDate, false);
                if (articles.isBlank() || articles.startsWith("No articles found")) return articles;
                return geminiService.summarizeArticles(articles);
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false);
            String result = task.getValue();
            resultArea.setText(result);
            updateWordStats(query, result);
        });

        task.setOnFailed(e -> {
            setLoading(false);
            resultArea.setText("Something went wrong. Check your API keys and internet connection.");
        });

        new Thread(task).start();
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        resultArea.setVisible(!loading);
        if (loading) wordStatsLabel.setText("");
    }

    private void updateWordStats(String input, String output) {
        int inW  = countWords(input);
        int outW = countWords(output);
        int pct  = inW > 0 ? (outW * 100 / inW) : 0;
        wordStatsLabel.setText("Query: " + inW + " words  →  Summary: " + outW + " words  (" + pct + "% of input)");
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }
}
