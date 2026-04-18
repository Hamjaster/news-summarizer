/*
 * This is the base blueprint for all content analyzers.
 * Every type of content like songs, books, and newspapers
 * must follow this blueprint and provide their own way of
 * analyzing content.
 */
package com.example.newssummarizer;

import java.util.ArrayList;
import java.util.List;

public abstract class ContentAnalyzer {

    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String FALLBACK_RESPONSE = "Could not complete request. Please try again.";

    protected final GeminiService geminiService;

    /**
     * Stores the shared Gemini service used by analyzer implementations.
     *
     * @param geminiService Shared Gemini service instance.
     * @return A ready analyzer base object.
     */
    protected ContentAnalyzer(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    /**
     * Runs the core analysis logic for one analyzer type.
     *
     * @param content User-provided content text.
     * @return Analysis result text.
     */
    public abstract String analyze(String content);

    /**
     * Returns the display label for this analyzer type.
     *
     * @param none This method does not receive arguments.
     * @return Default content label.
     */
    public String getTypeName() {
        return "Content";
    }

    /**
     * Prints default analyzer options in the shared 80-width terminal style.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to terminal.
     */
    public void printAnalysisMenu() {
        List<String> lines = new ArrayList<>();
        lines.add("[1]  Analyze Content");
        lines.add("[0]  Back");

        TerminalUtils.printTitledBox(
                "CONTENT ANALYZER",
                lines,
                CYAN + BOLD,
                CYAN + BOLD,
                WHITE,
                RESET
        );
    }

    /**
     * Sends a custom analysis prompt through Gemini and returns safe fallback on failure.
     *
     * @param prompt Prompt text to send.
     * @return Gemini response text or fallback response on errors.
     */
    protected String summarizePromptQuietly(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            return FALLBACK_RESPONSE;
        }

        try {
            return geminiService.generateFromPrompt(prompt);
        } catch (Exception exception) {
            return FALLBACK_RESPONSE;
        }
    }
}

// Analyzes song lyrics. Can summarize the main message, explain what the artist is feeling, and suggest creative things that could be done with the lyrics.
class LyricsAnalyzer extends ContentAnalyzer {

    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

    /**
     * Creates a lyrics analyzer using the shared Gemini service.
     *
     * @param geminiService Shared Gemini service instance.
     * @return A ready lyrics analyzer.
     */
    LyricsAnalyzer(GeminiService geminiService) {
        super(geminiService);
    }

    /**
     * Returns the display name for this analyzer type.
     *
     * @param none This method does not receive arguments.
     * @return Song lyrics type name.
     */
    @Override
    public String getTypeName() {
        return "Song Lyrics";
    }

    /**
     * Analyzes the lyrics for message, emotion, and themes.
     *
     * @param content Full lyrics text.
     * @return Concise lyrics analysis.
     */
    @Override
    public String analyze(String content) {
        String prompt = "You are a music analyst. Read these song lyrics and give a clear analysis: what is the main message, what emotion or story is the artist conveying, and what themes are present. Keep it concise and easy to understand.\n\nLyrics:\n"
                + content;
        return summarizePromptQuietly(prompt);
    }

    /**
     * Provides a short view into artist style and emotional state from lyrics.
     *
     * @param lyrics Full lyrics text.
     * @return One short insights paragraph.
     */
    public String getArtistInsights(String lyrics) {
        String prompt = "Based on these lyrics, what can you tell me about the artist's style, influences, and what they might be going through emotionally? Give a short paragraph.\n\nLyrics:\n"
                + lyrics;
        return summarizePromptQuietly(prompt);
    }

    /**
     * Suggests creative ways to use the song concept.
     *
     * @param lyrics Full lyrics text.
     * @return Three concise creative suggestions.
     */
    public String suggestCreativeUses(String lyrics) {
        String prompt = "Based on these lyrics, suggest 3 creative things that could be done with this song. For example: music video concepts, cover versions, remixes, or artistic interpretations. Keep suggestions short and interesting.\n\nLyrics:\n"
                + lyrics;
        return summarizePromptQuietly(prompt);
    }

    /**
     * Prints the lyrics analyzer action menu.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to the terminal.
     */
    @Override
    public void printAnalysisMenu() {
        List<String> lines = new ArrayList<>();
        lines.add("[1]  Analyze Main Message");
        lines.add("[2]  Artist Insights");
        lines.add("[3]  Creative Suggestions");
        lines.add("[0]  Back");

        TerminalUtils.printTitledBox(
                "SONG LYRICS ANALYZER",
                lines,
                CYAN + BOLD,
                CYAN + BOLD,
                WHITE,
                RESET
        );
    }
}

// Analyzes book excerpts or passages. Summarizes content, identifies themes, and explains the author's writing style.
class BookAnalyzer extends ContentAnalyzer {

    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

    /**
     * Creates a book analyzer using the shared Gemini service.
     *
     * @param geminiService Shared Gemini service instance.
     * @return A ready book analyzer.
     */
    BookAnalyzer(GeminiService geminiService) {
        super(geminiService);
    }

    /**
     * Returns the display name for this analyzer type.
     *
     * @param none This method does not receive arguments.
     * @return Book excerpt type name.
     */
    @Override
    public String getTypeName() {
        return "Book Excerpt";
    }

    /**
     * Summarizes the excerpt, extracts themes, and comments on style.
     *
     * @param content Full excerpt text.
     * @return Clear literary analysis.
     */
    @Override
    public String analyze(String content) {
        String prompt = "You are a literary analyst. Read this book excerpt and provide a clear summary of what is happening, identify the main themes, and briefly describe the writing style.\n\nExcerpt:\n"
                + content;
        return summarizePromptQuietly(prompt);
    }

    /**
     * Identifies up to three key themes and explains them briefly.
     *
     * @param text Full excerpt text.
     * @return Theme list with concise explanations.
     */
    public String getThemes(String text) {
        String prompt = "What are the main themes in this text? List up to 3 themes and explain each in one sentence.\n\nText:\n"
                + text;
        return summarizePromptQuietly(prompt);
    }

    /**
     * Prints the book analyzer action menu.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to the terminal.
     */
    @Override
    public void printAnalysisMenu() {
        List<String> lines = new ArrayList<>();
        lines.add("[1]  Summarize Excerpt");
        lines.add("[2]  Identify Themes");
        lines.add("[0]  Back");

        TerminalUtils.printTitledBox(
                "BOOK EXCERPT ANALYZER",
                lines,
                CYAN + BOLD,
                CYAN + BOLD,
                WHITE,
                RESET
        );
    }
}

// Analyzes newspaper articles or any pasted news content. Gives an objective summary and identifies key facts.
class NewspaperAnalyzer extends ContentAnalyzer {

    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

    /**
     * Creates a newspaper analyzer using the shared Gemini service.
     *
     * @param geminiService Shared Gemini service instance.
     * @return A ready newspaper analyzer.
     */
    NewspaperAnalyzer(GeminiService geminiService) {
        super(geminiService);
    }

    /**
     * Returns the display name for this analyzer type.
     *
     * @param none This method does not receive arguments.
     * @return Newspaper article type name.
     */
    @Override
    public String getTypeName() {
        return "Newspaper Article";
    }

    /**
     * Produces an objective summary with key context for the article.
     *
     * @param content Full article text.
     * @return Clear objective summary.
     */
    @Override
    public String analyze(String content) {
        String prompt = "You are a news analyst. Read this newspaper article and give a clear objective summary. Identify the key facts, who is involved, and what the main event or issue is.\n\nArticle:\n"
                + content;
        return summarizePromptQuietly(prompt);
    }

    /**
     * Extracts the most important facts from the article.
     *
     * @param text Full article text.
     * @return Three to five key facts as short sentences.
     */
    public String getKeyFacts(String text) {
        String prompt = "From this news article, extract the 3 to 5 most important facts. Present each fact in one clear sentence.\n\nArticle:\n"
                + text;
        return summarizePromptQuietly(prompt);
    }

    /**
     * Prints the newspaper analyzer action menu.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to the terminal.
     */
    @Override
    public void printAnalysisMenu() {
        List<String> lines = new ArrayList<>();
        lines.add("[1]  Summarize Article");
        lines.add("[2]  Extract Key Facts");
        lines.add("[0]  Back");

        TerminalUtils.printTitledBox(
                "NEWSPAPER ARTICLE ANALYZER",
                lines,
                CYAN + BOLD,
                CYAN + BOLD,
                WHITE,
                RESET
        );
    }
}
