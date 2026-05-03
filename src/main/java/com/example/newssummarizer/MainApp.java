package com.example.newssummarizer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        DotEnvLoader.load();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("MainLayout.fxml"));
        Scene scene = new Scene(loader.load(), 900, 620);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        stage.setTitle("News Summarizer");
        stage.setMinWidth(700);
        stage.setMinHeight(500);
        stage.setScene(scene);
        stage.show();
    }
}
