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
import java.util.List;
import java.util.Scanner;

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

    private static final class FetchResult {
        private final String combinedArticles;
        private final String sourceLabel;

        private FetchResult(String combinedArticles, String sourceLabel) {
            this.combinedArticles = combinedArticles;
            this.sourceLabel = sourceLabel;
        }
    }

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
            FetchResult morningArticles = fetchMorningArticlesWithFallback(today);
            if (!hasArticles(morningArticles.combinedArticles)) {
                printErrorBox(
                        "ERROR: Morning Briefing could not find enough articles.",
                        "Please try again in a few minutes."
                );
                return;
            }

            String briefingSummary = summarizeMorningBriefing(morningArticles.combinedArticles);
            TerminalUtils.printSummaryBox(
                    "MORNING BRIEFING",
                    briefingSummary,
                    CYAN + BOLD,
                    CYAN + BOLD,
                    WHITE,
                    RESET
            );

            TerminalUtils.printWordStats(morningArticles.combinedArticles, briefingSummary, YELLOW, RESET);

            int articleCount = countArticles(morningArticles.combinedArticles);
            String sourceLine = "Sources combined: " + articleCount + " articles from " + morningArticles.sourceLabel;
            TerminalUtils.printCenteredLine(sourceLine, GREEN + BOLD, RESET);
            TerminalUtils.printGreenDivider(GREEN, RESET);
            System.out.println();

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
        TerminalUtils.printCenteredLine("═".repeat(TerminalUtils.BOX_WIDTH), CYAN + BOLD, RESET);
        TerminalUtils.printCenteredLine("MORNING BRIEFING", CYAN + BOLD, RESET);
        TerminalUtils.printCenteredLine("Fetching today's top stories from around the world...", CYAN, RESET);
        TerminalUtils.printCenteredLine("═".repeat(TerminalUtils.BOX_WIDTH), CYAN + BOLD, RESET);
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
        return newsApiService.fetchArticles(keywords, dateText, dateText, false);
    }

    /**
     * Tries multiple date/query combinations to avoid empty morning briefings.
     *
     * @param today Current date used as anchor.
     * @return Best fetch result found.
     */
    private FetchResult fetchMorningArticlesWithFallback(LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        LocalDate threeDaysAgo = today.minusDays(2);
        LocalDate sevenDaysAgo = today.minusDays(6);

        List<String> queries = new ArrayList<>();
        queries.add("world news today");
        queries.add("world news");
        queries.add("international news");
        queries.add("global news");
        queries.add("breaking news");
        queries.add("news");

        for (String query : queries) {
            String todayArticles = fetchArticlesForDate(query, today);
            if (hasArticles(todayArticles)) {
                return new FetchResult(todayArticles, "today");
            }

            String yesterdayArticles = fetchArticlesForDate(query, yesterday);
            if (hasArticles(yesterdayArticles)) {
                return new FetchResult(yesterdayArticles, "yesterday");
            }
        }

        String recentFrom = threeDaysAgo.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String recentTo = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        for (String query : queries) {
            String recentArticles = newsApiService.fetchArticles(query, recentFrom, recentTo, false);
            if (hasArticles(recentArticles)) {
                return new FetchResult(recentArticles, "last 3 days");
            }
        }

        String weeklyFrom = sevenDaysAgo.format(DateTimeFormatter.ISO_LOCAL_DATE);
        for (String query : queries) {
            String weeklyArticles = newsApiService.fetchArticles(query, weeklyFrom, recentTo, false);
            if (hasArticles(weeklyArticles)) {
                return new FetchResult(weeklyArticles, "last 7 days");
            }
        }

        return new FetchResult(NO_ARTICLES_MESSAGE, "recent days");
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
        TerminalUtils.printCenteredLine("Want to dig deeper into any of these topics", WHITE, RESET);
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
            FetchResult topicArticles = fetchTopicArticlesWithFallback(topic, date);

            if (!hasArticles(topicArticles.combinedArticles)) {
                printErrorBox(
                        "ERROR: No recent articles were found for that topic.",
                        "Try another topic keyword."
                );
                return;
            }

            String summary = geminiService.summarizeArticles(topicArticles.combinedArticles);
            if (isGeminiFallback(summary)) {
                summary = buildLocalHeadlineSummary(topicArticles.combinedArticles);
            }

            TerminalUtils.printSummaryBox(
                    "TOPIC DEEP DIVE",
                    summary,
                    CYAN + BOLD,
                    CYAN + BOLD,
                    WHITE,
                    RESET
            );

                    TerminalUtils.printWordStats(topicArticles.combinedArticles, summary, YELLOW, RESET);

                    int articleCount = countArticles(topicArticles.combinedArticles);
            TerminalUtils.printCenteredLine(
                        "Sources combined: " + articleCount + " articles from " + topicArticles.sourceLabel,
                    GREEN + BOLD,
                    RESET
            );
            TerminalUtils.printGreenDivider(GREEN, RESET);
        } catch (Exception exception) {
            printErrorBox(
                    "ERROR: Topic deep-dive failed unexpectedly.",
                    "Please try another topic."
            );
        }
    }

    /**
     * Tries topic fetches in a wider range before giving up.
     *
     * @param topic Topic entered by the user.
     * @param today Current date used as anchor.
     * @return Best topic fetch result found.
     */
    private FetchResult fetchTopicArticlesWithFallback(String topic, LocalDate today) {
        LocalDate yesterday = today.minusDays(1);
        LocalDate threeDaysAgo = today.minusDays(2);
        LocalDate sevenDaysAgo = today.minusDays(6);

        String todayText = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String yesterdayText = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String threeDaysAgoText = threeDaysAgo.format(DateTimeFormatter.ISO_LOCAL_DATE);

        String todayArticles = newsApiService.fetchArticles(topic, todayText, todayText, false);
        if (hasArticles(todayArticles)) {
            return new FetchResult(todayArticles, "today");
        }

        String yesterdayArticles = newsApiService.fetchArticles(topic, yesterdayText, yesterdayText, false);
        if (hasArticles(yesterdayArticles)) {
            return new FetchResult(yesterdayArticles, "yesterday");
        }

        String recentArticles = newsApiService.fetchArticles(topic, threeDaysAgoText, todayText, false);
        if (hasArticles(recentArticles)) {
            return new FetchResult(recentArticles, "last 3 days");
        }

        String withNewsKeyword = topic + " news";
        String recentWithKeyword = newsApiService.fetchArticles(withNewsKeyword, threeDaysAgoText, todayText, false);
        if (hasArticles(recentWithKeyword)) {
            return new FetchResult(recentWithKeyword, "last 3 days");
        }

        String sevenDaysAgoText = sevenDaysAgo.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String weeklyArticles = newsApiService.fetchArticles(topic, sevenDaysAgoText, todayText, false);
        if (hasArticles(weeklyArticles)) {
            return new FetchResult(weeklyArticles, "last 7 days");
        }

        String weeklyWithKeyword = newsApiService.fetchArticles(withNewsKeyword, sevenDaysAgoText, todayText, false);
        if (hasArticles(weeklyWithKeyword)) {
            return new FetchResult(weeklyWithKeyword, "last 7 days");
        }

        return new FetchResult(NO_ARTICLES_MESSAGE, "recent days");
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
     * Checks whether the fetched text contains usable article content.
     *
     * @param value NewsAPI combined text.
     * @return True when content is usable.
     */
    private boolean hasArticles(String value) {
        return value != null && !value.isBlank() && !isNoArticles(value);
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