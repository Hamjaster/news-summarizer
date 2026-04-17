package com.example.newssummarizer.model;

/**
 * Normalized article object used throughout the app.
 *
 * This object is created from external API responses and later passed
 * to LLM summarization.
 */
public class NewsArticle {

    // Headline of the article.
    private String title;

    // Short description/snippet.
    private String description;

    // Publisher or source name.
    private String source;

    // Link to full article.
    private String url;

    // Optional long-form content (if provided by API).
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}