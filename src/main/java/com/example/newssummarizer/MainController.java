package com.example.newssummarizer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class MainController {

    @FXML private StackPane contentArea;

    private GeminiService geminiService;
    private NewsApiService newsApiService;

    // cached views + controllers
    private Parent newsSearchView;
    private NewsSearchController newsSearchCtrl;

    private Parent textSummaryView;
    private TextSummaryController textSummaryCtrl;

    private Parent contentAnalyzerView;
    private ContentAnalyzerController contentAnalyzerCtrl;

    private Parent morningBriefingView;
    private MorningBriefingController morningBriefingCtrl;

    @FXML
    public void initialize() {
        geminiService = new GeminiService();
        newsApiService = new NewsApiService();
        showWelcome();
    }

    @FXML
    private void showSearchNews() {
        if (newsSearchView == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("NewsSearchView.fxml"));
                newsSearchView = loader.load();
                newsSearchCtrl = loader.getController();
                newsSearchCtrl.setServices(geminiService, newsApiService);
            } catch (IOException e) {
                showError("Could not load Search News view.");
                return;
            }
        }
        contentArea.getChildren().setAll(newsSearchView);
    }

    @FXML
    private void showSummarizeText() {
        if (textSummaryView == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("TextSummaryView.fxml"));
                textSummaryView = loader.load();
                textSummaryCtrl = loader.getController();
                textSummaryCtrl.setServices(geminiService);
            } catch (IOException e) {
                showError("Could not load Summarize Text view.");
                return;
            }
        }
        contentArea.getChildren().setAll(textSummaryView);
    }

    @FXML
    private void showAnalyzeContent() {
        if (contentAnalyzerView == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("ContentAnalyzerView.fxml"));
                contentAnalyzerView = loader.load();
                contentAnalyzerCtrl = loader.getController();
                contentAnalyzerCtrl.setServices(geminiService);
            } catch (IOException e) {
                showError("Could not load Analyze Content view.");
                return;
            }
        }
        contentArea.getChildren().setAll(contentAnalyzerView);
    }

    @FXML
    private void showMorningBriefing() {
        if (morningBriefingView == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("MorningBriefingView.fxml"));
                morningBriefingView = loader.load();
                morningBriefingCtrl = loader.getController();
                morningBriefingCtrl.setServices(geminiService, newsApiService);
            } catch (IOException e) {
                showError("Could not load Morning Briefing view.");
                return;
            }
        }
        contentArea.getChildren().setAll(morningBriefingView);
    }

    @FXML
    private void exitApp() {
        Platform.exit();
    }

    private void showWelcome() {
        Label label = new Label("Welcome to News Summarizer\nSelect a feature from the sidebar to get started.");
        label.getStyleClass().add("welcome-text");
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        contentArea.getChildren().setAll(label);
    }

    private void showError(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("error-text");
        contentArea.getChildren().setAll(label);
    }
}
