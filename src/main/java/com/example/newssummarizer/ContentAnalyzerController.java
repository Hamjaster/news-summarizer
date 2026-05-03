package com.example.newssummarizer;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ContentAnalyzerController {

    @FXML private RadioButton lyricsRadio;
    @FXML private RadioButton bookRadio;
    @FXML private RadioButton newspaperRadio;
    @FXML private ComboBox<String> actionCombo;
    @FXML private TextArea inputArea;
    @FXML private TextArea resultArea;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label wordStatsLabel;

    private GeminiService geminiService;

    private LyricsAnalyzer    lyricsAnalyzer;
    private BookAnalyzer      bookAnalyzer;
    private NewspaperAnalyzer newspaperAnalyzer;

    public void setServices(GeminiService gemini) {
        this.geminiService    = gemini;
        this.lyricsAnalyzer    = new LyricsAnalyzer(gemini);
        this.bookAnalyzer      = new BookAnalyzer(gemini);
        this.newspaperAnalyzer = new NewspaperAnalyzer(gemini);
        updateActionOptions();
    }

    @FXML
    public void initialize() {
        lyricsRadio.setOnAction(e    -> updateActionOptions());
        bookRadio.setOnAction(e      -> updateActionOptions());
        newspaperRadio.setOnAction(e -> updateActionOptions());
    }

    private void updateActionOptions() {
        actionCombo.getItems().clear();
        if (lyricsRadio.isSelected()) {
            actionCombo.getItems().addAll("Main Message", "Artist Insights", "Creative Suggestions");
        } else if (bookRadio.isSelected()) {
            actionCombo.getItems().addAll("Summarize Excerpt", "Identify Themes");
        } else {
            actionCombo.getItems().addAll("Summarize Article", "Extract Key Facts");
        }
        actionCombo.getSelectionModel().selectFirst();
    }

    @FXML
    private void onAnalyze() {
        String content = inputArea.getText().trim();
        if (content.isEmpty()) return;
        String action = actionCombo.getValue();

        setLoading(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                if (lyricsRadio.isSelected()) {
                    return switch (action) {
                        case "Artist Insights"      -> lyricsAnalyzer.getArtistInsights(content);
                        case "Creative Suggestions" -> lyricsAnalyzer.suggestCreativeUses(content);
                        default                     -> lyricsAnalyzer.analyze(content);
                    };
                } else if (bookRadio.isSelected()) {
                    return switch (action) {
                        case "Identify Themes" -> bookAnalyzer.getThemes(content);
                        default                -> bookAnalyzer.analyze(content);
                    };
                } else {
                    return switch (action) {
                        case "Extract Key Facts" -> newspaperAnalyzer.getKeyFacts(content);
                        default                  -> newspaperAnalyzer.analyze(content);
                    };
                }
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false);
            String result = surfaceFailureReason(task.getValue());
            resultArea.setText(result);
            updateWordStats(content, result);
        });

        task.setOnFailed(e -> {
            setLoading(false);
            resultArea.setText("Something went wrong. Check your API key and connection.");
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
        wordStatsLabel.setText("Input: " + inW + " words  →  Result: " + outW + " words  (" + pct + "% of input)");
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private String surfaceFailureReason(String result) {
        if (result == null || result.isBlank() || result.startsWith("Could not complete request")) {
            String reason = geminiService.getLastFailureReason();
            if (reason != null && !reason.isBlank()) return reason;
        }
        return result;
    }
}
