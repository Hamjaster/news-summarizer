package com.example.newssummarizer;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class MorningBriefingController {

    @FXML private TextArea briefingArea;
    @FXML private ProgressIndicator briefingProgress;
    @FXML private TextField deepDiveField;
    @FXML private TextArea deepDiveArea;
    @FXML private ProgressIndicator deepDiveProgress;

    private GeminiService geminiService;
    private NewsApiService newsApiService;

    private static final String NO_ARTICLES = "No articles found for this query and date range.";
    private static final String MORNING_INSTRUCTION =
        "You are a morning news anchor. Read these headlines and write a clean, engaging morning briefing "
        + "in 3 to 4 paragraphs. Cover the most important world events. Use simple language. "
        + "Sound like a professional but friendly news anchor summarizing the day's top stories.";

    public void setServices(GeminiService gemini, NewsApiService newsApi) {
        this.geminiService  = gemini;
        this.newsApiService = newsApi;
    }

    @FXML
    private void onGetBriefing() {
        briefingProgress.setVisible(true);
        briefingArea.setVisible(false);
        briefingArea.setText("");

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                String articles = fetchMorningArticles();
                if (articles.isBlank()) return "Could not fetch articles. Check your connection and try again.";
                return geminiService.summarizeArticles(articles, MORNING_INSTRUCTION);
            }
        };

        task.setOnSucceeded(e -> {
            briefingProgress.setVisible(false);
            briefingArea.setVisible(true);
            briefingArea.setText(task.getValue());
        });

        task.setOnFailed(e -> {
            briefingProgress.setVisible(false);
            briefingArea.setVisible(true);
            briefingArea.setText("Something went wrong. Check your API key and connection.");
        });

        new Thread(task).start();
    }

    @FXML
    private void onDeepDive() {
        String topic = deepDiveField.getText().trim();
        if (topic.isEmpty()) return;

        deepDiveProgress.setVisible(true);
        deepDiveArea.setVisible(false);
        deepDiveArea.setText("");

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                String articles = fetchTopicArticles(topic);
                if (articles.isBlank()) return "No recent articles found for \"" + topic + "\". Try a different topic.";
                return geminiService.summarizeArticles(articles);
            }
        };

        task.setOnSucceeded(e -> {
            deepDiveProgress.setVisible(false);
            deepDiveArea.setVisible(true);
            deepDiveArea.setText(task.getValue());
        });

        task.setOnFailed(e -> {
            deepDiveProgress.setVisible(false);
            deepDiveArea.setVisible(true);
            deepDiveArea.setText("Something went wrong. Check your API key and connection.");
        });

        new Thread(task).start();
    }

    private String fetchMorningArticles() {
        LocalDate today = LocalDate.now();
        String toDate   = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String fromDate = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);

        String[] queries = {"world news", "international news", "global news", "breaking news", "news"};
        for (String q : queries) {
            String result = newsApiService.fetchArticles(q, fromDate, toDate, false);
            if (hasArticles(result)) return result;
        }

        // wider window fallback — last 7 days
        fromDate = today.minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE);
        for (String q : queries) {
            String result = newsApiService.fetchArticles(q, fromDate, toDate, false);
            if (hasArticles(result)) return result;
        }

        return "";
    }

    private String fetchTopicArticles(String topic) {
        LocalDate today     = LocalDate.now();
        String toDate       = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String from3Days    = today.minusDays(2).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String from7Days    = today.minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE);

        String result = newsApiService.fetchArticles(topic, from3Days, toDate, false);
        if (hasArticles(result)) return result;

        result = newsApiService.fetchArticles(topic + " news", from3Days, toDate, false);
        if (hasArticles(result)) return result;

        result = newsApiService.fetchArticles(topic, from7Days, toDate, false);
        if (hasArticles(result)) return result;

        return "";
    }

    private boolean hasArticles(String value) {
        return value != null && !value.isBlank() && !NO_ARTICLES.equalsIgnoreCase(value.trim());
    }
}
