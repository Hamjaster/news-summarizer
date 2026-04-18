/*
 * This file handles the Morning Briefing feature.
 * It automatically fetches today's top world news without the user needing to enter a query,
 * combines all headlines, and gives one clean summary
 * of what is happening in the world right now.
 */
package com.example.newssummarizer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class MorningBriefing {

    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String NO_ARTICLES_MESSAGE = "No articles found for this query and date range.";
    private static final String GEMINI_FALLBACK_RESPONSE = "Could not complete request. Please try again.";

    private final Scanner scanner;
    private final GeminiService geminiService;
    private final NewsApiService newsApiService;

    /**
     * Creates the Morning Briefing controller with shared services.
     *
     * @param scanner Shared scanner from the main menu.
     * @param geminiService Shared Gemini service instance.
     * @param newsApiService Shared NewsAPI service instance.
     * @return A ready MorningBriefing object.
     */
    public MorningBriefing(Scanner scanner, GeminiService geminiService, NewsApiService newsApiService) {
        this.scanner = scanner;
        this.geminiService = geminiService;
        this.newsApiService = newsApiService;
    }

    /**
     * Runs the full morning briefing flow from fetch to optional topic deep dive.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It returns control to the main menu when done.
     */
    public void start() {
        try {
            showMorningBanner();

            LocalDate today = LocalDate.now();
            LocalDate usedDate = today;
            String sourceLabel = "today";

            String combinedArticles = fetchArticlesForDate("world news today", today);
            if (isNoArticles(combinedArticles)) {
                usedDate = today.minusDays(1);
                sourceLabel = "yesterday";
                combinedArticles = fetchArticlesForDate("world news today", usedDate);
            }

            if (isNoArticles(combinedArticles) || combinedArticles == null || combinedArticles.isBlank()) {
                printErrorBox(
                        "ERROR: Morning Briefing could not find enough articles.",
                        "Please try again in a few minutes."
                );
                return;
            }

            String briefingSummary = summarizeMorningBriefing(combinedArticles);
            TerminalUtils.printSummaryBox(
                    "MORNING BRIEFING",
                    briefingSummary,
                    CYAN + BOLD,
                    CYAN + BOLD,
                    WHITE,
                    RESET
            );

            TerminalUtils.printWordStats(combinedArticles, briefingSummary, YELLOW, RESET);

            int articleCount = countArticles(combinedArticles);
            String sourceLine = "Sources combined: " + articleCount + " articles from " + sourceLabel;
            TerminalUtils.printCenteredLine(sourceLine, GREEN + BOLD, RESET);

            String topKeywords = resolveTopKeywords(briefingSummary);
            TerminalUtils.printTopKeywords(topKeywords, YELLOW, BOLD, RESET);
            TerminalUtils.printGreenDivider(GREEN, RESET);

            promptDeepDive(today);
        } catch (Exception exception) {
            printErrorBox(
                    "ERROR: Morning Briefing failed unexpectedly.",
                    "Please try again."
            );
        }
    }

    /**
     * Displays the themed Morning Briefing banner before automatic fetching begins.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to terminal.
     */
    private void showMorningBanner() {
        System.out.println();
        TerminalUtils.printCenteredLine("═".repeat(TerminalUtils.BOX_WIDTH), CYAN + BOLD, RESET);
        TerminalUtils.printCenteredLine("", WHITE, RESET);
        TerminalUtils.printCenteredLine("MORNING BRIEFING", CYAN + BOLD, RESET);
        TerminalUtils.printCenteredLine("", WHITE, RESET);
        TerminalUtils.printCenteredLine("Fetching today's top stories from around the world...", CYAN, RESET);
        TerminalUtils.printCenteredLine("", WHITE, RESET);
        TerminalUtils.printCenteredLine("═".repeat(TerminalUtils.BOX_WIDTH), CYAN + BOLD, RESET);
        System.out.println();
    }

    /**
     * Fetches world news for a single specific date.
     *
     * @param keywords Search keywords for NewsAPI.
     * @param date Date used for both from and to parameters.
     * @return Combined article text or a no-articles marker.
     */
    private String fetchArticlesForDate(String keywords, LocalDate date) {
        String dateText = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return newsApiService.fetchArticles(keywords, dateText, dateText);
    }

    /**
     * Builds and requests the specific morning-anchor style summary.
     *
     * @param combinedArticles Combined article titles and descriptions.
     * @return Morning briefing summary text.
     */
    private String summarizeMorningBriefing(String combinedArticles) {
        String instruction = "You are a morning news anchor. Read these headlines and write a clean, engaging morning briefing in 3 to 4 paragraphs. "
            + "Cover the most important world events. Use simple language. Sound like a professional but friendly news anchor summarizing the day's top stories.";

        String geminiResult = geminiService.summarizeArticles(combinedArticles, instruction);
        if (isGeminiFallback(geminiResult)) {
            return buildLocalHeadlineSummary(combinedArticles);
        }

        return geminiResult;
    }

    /**
     * Shows the deep-dive prompt and optionally runs a same-day topic search.
     *
     * @param today Current date used for same-day deep-dive search.
     * @return Nothing. Control returns to main menu after this step.
     */
    private void promptDeepDive(LocalDate today) {
        TerminalUtils.printCenteredLine("═".repeat(TerminalUtils.BOX_WIDTH), CYAN + BOLD, RESET);
        TerminalUtils.printCenteredLine("Want to dig deeper into any of these topics?", WHITE, RESET);
        TerminalUtils.printCenteredLine("Type a topic and press Enter, or press Enter to go back to main menu:", WHITE, RESET);
        TerminalUtils.printCenteredLine("═".repeat(TerminalUtils.BOX_WIDTH), CYAN + BOLD, RESET);

        String topic = TerminalUtils.prompt(scanner, "Topic:", CYAN, WHITE, RESET);
        if (topic.isBlank()) {
            return;
        }

        runTopicDeepDive(topic, today);
    }

    /**
     * Executes a same-day topic search and prints a full summary section.
     *
     * @param topic User-selected topic.
     * @param date Date used for same-day search window.
     * @return Nothing. It prints results directly to terminal.
     */
    private void runTopicDeepDive(String topic, LocalDate date) {
        try {
            String dateText = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String combinedArticles = newsApiService.fetchArticles(topic, dateText, dateText);

            if (isNoArticles(combinedArticles)) {
                printErrorBox(
                        "ERROR: No same-day articles were found for that topic.",
                        "Try another topic keyword."
                );
                return;
            }

            if (combinedArticles == null || combinedArticles.isBlank()) {
                printErrorBox(
                        "ERROR: Topic search failed due to a NewsAPI issue.",
                        "Please try again."
                );
                return;
            }

            String summary = geminiService.summarizeArticles(combinedArticles);
            if (isGeminiFallback(summary)) {
                summary = buildLocalHeadlineSummary(combinedArticles);
            }

            TerminalUtils.printSummaryBox(
                    "TOPIC DEEP DIVE",
                    summary,
                    CYAN + BOLD,
                    CYAN + BOLD,
                    WHITE,
                    RESET
            );

            TerminalUtils.printWordStats(combinedArticles, summary, YELLOW, RESET);

            int articleCount = countArticles(combinedArticles);
            TerminalUtils.printCenteredLine(
                    "Sources combined: " + articleCount + " articles from today",
                    GREEN + BOLD,
                    RESET
            );

            String topKeywords = resolveTopKeywords(summary);
            TerminalUtils.printTopKeywords(topKeywords, YELLOW, BOLD, RESET);
            TerminalUtils.printGreenDivider(GREEN, RESET);
        } catch (Exception exception) {
            printErrorBox(
                    "ERROR: Topic deep-dive failed unexpectedly.",
                    "Please try another topic."
            );
        }
    }

    /**
     * Resolves top keywords via Gemini first, then falls back to local extraction.
     *
     * @param summaryText Summary content used for keyword extraction.
     * @return Comma-separated keyword text.
     */
    private String resolveTopKeywords(String summaryText) {
        String keywords = geminiService.extractTopKeywords(summaryText);
        if (isGeminiFallback(keywords) || keywords.isBlank()) {
            return buildLocalKeywords(summaryText);
        }
        return keywords;
    }

    /**
     * Creates a local fallback summary from headline titles when Gemini is unavailable.
     *
     * @param combinedArticles Combined article data.
     * @return A concise locally built summary.
     */
    private String buildLocalHeadlineSummary(String combinedArticles) {
        String[] lines = combinedArticles.split("\\r?\\n");
        List<String> titles = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("Title:")) {
                String title = line.substring("Title:".length()).trim();
                if (!title.isEmpty() && !"N/A".equalsIgnoreCase(title)) {
                    titles.add(title);
                }
            }
            if (titles.size() >= 6) {
                break;
            }
        }

        if (titles.isEmpty()) {
            return "Morning Briefing is temporarily running in fallback mode because Gemini is unavailable.";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Gemini is currently unavailable, so this briefing is built directly from top headlines: ");
        for (int index = 0; index < titles.size(); index++) {
            builder.append(titles.get(index));
            if (index < titles.size() - 1) {
                builder.append(". ");
            } else {
                builder.append('.');
            }
        }

        return builder.toString();
    }

    /**
     * Builds local keyword candidates from frequency counts in summary text.
     *
     * @param text Summary text source.
     * @return Five comma-separated keywords.
     */
    private String buildLocalKeywords(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return "news, world, update, events, briefing";
        }

        Set<String> stopWords = new HashSet<>();
        stopWords.add("the");
        stopWords.add("and");
        stopWords.add("for");
        stopWords.add("with");
        stopWords.add("from");
        stopWords.add("that");
        stopWords.add("this");
        stopWords.add("into");
        stopWords.add("about");
        stopWords.add("have");
        stopWords.add("has");
        stopWords.add("are");
        stopWords.add("was");
        stopWords.add("were");

        Map<String, Integer> counts = new HashMap<>();
        for (String word : normalized.split(" ")) {
            if (word.length() < 3 || stopWords.contains(word)) {
                continue;
            }
            counts.put(word, counts.getOrDefault(word, 0) + 1);
        }

        if (counts.isEmpty()) {
            return "news, world, update, events, briefing";
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((left, right) -> {
            int compareCount = Integer.compare(right.getValue(), left.getValue());
            if (compareCount != 0) {
                return compareCount;
            }
            return left.getKey().compareTo(right.getKey());
        });

        List<String> topKeywords = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : entries) {
            topKeywords.add(entry.getKey());
            if (topKeywords.size() == 5) {
                break;
            }
        }

        while (topKeywords.size() < 5) {
            topKeywords.add("news");
        }

        return String.join(", ", topKeywords);
    }

    /**
     * Counts headline entries from combined NewsAPI text.
     *
     * @param combinedArticles Combined article text.
     * @return Number of article titles detected.
     */
    private int countArticles(String combinedArticles) {
        if (combinedArticles == null || combinedArticles.isBlank()) {
            return 0;
        }

        int count = 0;
        for (String line : combinedArticles.split("\\r?\\n")) {
            if (line.startsWith("Title:")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks if Gemini returned the common fallback response.
     *
     * @param value Gemini response text.
     * @return True when fallback text is detected.
     */
    private boolean isGeminiFallback(String value) {
        if (value == null) {
            return true;
        }
        return GEMINI_FALLBACK_RESPONSE.equalsIgnoreCase(value.trim());
    }

    /**
     * Checks whether the fetch response indicates no matching articles.
     *
     * @param value NewsAPI combined text.
     * @return True when response is the no-articles marker.
     */
    private boolean isNoArticles(String value) {
        if (value == null) {
            return true;
        }
        return NO_ARTICLES_MESSAGE.equalsIgnoreCase(value.trim());
    }

    /**
     * Prints a red bordered error message with consistent layout.
     *
     * @param lines Error lines to display.
     * @return Nothing. It prints directly to terminal.
     */
    private void printErrorBox(String... lines) {
        List<String> messageLines = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                messageLines.add(line == null ? "" : line);
            }
        }
        TerminalUtils.printSimpleBox(messageLines, RED + BOLD, RED, RESET);
    }
}