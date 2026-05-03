package com.example.newssummarizer;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class MainController {

    private static final String ACTIVE_NAV_CLASS = "nav-btn-active";

    @FXML private StackPane contentArea;
    @FXML private Button navSearch;
    @FXML private Button navSummary;
    @FXML private Button navAnalyze;
    @FXML private Button navBriefing;

    private GeminiService geminiService;
    private NewsApiService newsApiService;

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
        setActiveNav(navSearch);
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
        setActiveNav(navSummary);
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
        setActiveNav(navAnalyze);
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
        setActiveNav(navBriefing);
    }

    @FXML
    private void exitApp() {
        Platform.exit();
    }

    private void setActiveNav(Button active) {
        for (Button b : new Button[]{navSearch, navSummary, navAnalyze, navBriefing}) {
            b.getStyleClass().remove(ACTIVE_NAV_CLASS);
        }
        if (active != null && !active.getStyleClass().contains(ACTIVE_NAV_CLASS)) {
            active.getStyleClass().add(ACTIVE_NAV_CLASS);
        }
    }

    private void showWelcome() {
        VBox card = new VBox();
        card.getStyleClass().add("welcome-card");

        Label eyebrow = new Label("THE DAILY BRIEF · " + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d")).toUpperCase());
        eyebrow.getStyleClass().add("welcome-eyebrow");

        Label headline = new Label("All the news you need, written for the way you read.");
        headline.getStyleClass().add("welcome-headline");
        headline.setWrapText(true);

        Label deck = new Label(
                "Search the wires, summarize a long read, analyze a song or a chapter, "
                + "or grab a fresh morning briefing. Pick a section from the left to begin."
        );
        deck.getStyleClass().add("welcome-deck");
        deck.setWrapText(true);

        Label features = new Label(
                "◷  Search News   —   ask in plain English, get a paragraph briefing\n"
                + "❡  Summarize Text   —   paste anything, read the gist\n"
                + "✎  Analyze Content   —   lyrics, books, articles — multiple lenses\n"
                + "☀  Morning Briefing   —   today's top stories, then go deeper"
        );
        features.getStyleClass().add("welcome-feature");
        features.setWrapText(true);

        card.getChildren().addAll(eyebrow, headline, deck, features);

        StackPane wrapper = new StackPane(card);
        wrapper.getStyleClass().add("content-area");
        contentArea.getChildren().setAll(wrapper);
        setActiveNav(null);
    }

    private void showError(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("error-text");
        contentArea.getChildren().setAll(label);
    }
}
