package com.example.newssummarizer;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;

public class TextSummaryController {

    @FXML private TextArea inputArea;
    @FXML private TextArea resultArea;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label wordStatsLabel;
    @FXML private ComboBox<String> lengthCombo;

    private GeminiService geminiService;

    public void setServices(GeminiService gemini) {
        this.geminiService = gemini;
    }

    @FXML
    public void initialize() {
        lengthCombo.getItems().setAll("Short", "Medium", "Long");
        lengthCombo.getSelectionModel().select("Medium");
    }

    @FXML
    private void onSummarize() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;
        String length = lengthCombo.getValue();

        setLoading(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return geminiService.summarizeText(text, length);
            }
        };

        task.setOnSucceeded(e -> {
            setLoading(false);
            String result = surfaceFailureReason(task.getValue());
            resultArea.setText(result);
            updateWordStats(text, result);
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
        wordStatsLabel.setText("Original: " + inW + " words  →  Summary: " + outW + " words  (" + pct + "% of original)");
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
