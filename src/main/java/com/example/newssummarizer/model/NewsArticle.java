package com.example.newssummarizer.model;

public class NewsArticle {

    private String title;
    private String description;
    private String source;
    private String url;
    private String content;

    public NewsArticle() {
    }

    public NewsArticle(String title, String description, String source, String url) {
        this.title = title;
        this.description = description;
        this.source = source;
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}