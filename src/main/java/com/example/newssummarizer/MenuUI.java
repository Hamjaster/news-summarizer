/*
 * This file controls everything the user sees in the terminal.
 * It shows the main menu, takes user input, and sends them to either the News Summarizer or the Text Summarizer.
 * It validates dates and keeps the user flow clean and friendly.
 * It prints clear boxes for menus, summaries, and errors.
 */
package com.example.newssummarizer;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MenuUI {

    private static final int SUMMARY_TEXT_WIDTH = 40;
    private static final DateTimeFormatter OUTPUT_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<DateTimeFormatter> SUPPORTED_DATE_FORMATS = createDateFormatters();
    private static final Pattern COMPACT_RANGE_PATTERN = Pattern.compile("(?i)^\\s*(\\d{1,2})\\s*-\\s*(\\d{1,2})\\s+([a-zA-Z]+)\\s+(\\d{4})\\s*$");
    private static final String GEMINI_FALLBACK_RESPONSE = "Could not complete request. Please try again.";

    private final Scanner scanner;
    private final GeminiService geminiService;
    private final NewsApiService newsApiService;

    /**
     * Creates the menu UI with the services needed for all user actions.
     *
     * @param none This constructor does not receive arguments.
     * @return A ready MenuUI instance.
     */
    public MenuUI() {
        this.scanner = new Scanner(System.in);
        this.geminiService = new GeminiService();
        this.newsApiService = new NewsApiService();
    }

    /**
     * Starts the interactive terminal loop and handles menu navigation.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It exits when the user chooses option 0.
     */
    public void start() {
        boolean keepRunning = true;

        try {
            while (keepRunning) {
                showMainMenu();
                String menuChoice = readLine("Choose an option: ");

                if ("1".equals(menuChoice)) {
                    handleNewsSearchFlow();
                } else if ("2".equals(menuChoice)) {
                    handleTextSummarizerFlow();
                } else if ("0".equals(menuChoice)) {
                    keepRunning = false;
                    System.out.println("Goodbye.");
                } else {
                    printErrorBox(
                            "ERROR: Invalid menu option.",
                            "Please choose 1, 2, or 0."
                    );
                }
            }
        } catch (Exception exception) {
            printErrorBox(
                    "ERROR: The menu stopped unexpectedly.",
                    "Please restart the application and try again."
            );
        } finally {
            scanner.close();
        }
    }

    /**
     * Prints the main menu using an aesthetic bordered layout.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to the terminal.
     */
    private void showMainMenu() {
        System.out.println("\u2554" + "\u2550".repeat(38) + "\u2557");
        System.out.println("\u2551        NEWS & SUMMARY ASSISTANT      \u2551");
        System.out.println("\u2560" + "\u2550".repeat(38) + "\u2563");
        System.out.println("\u2551  [1]  Search & Summarize News        \u2551");
        System.out.println("\u2551  [2]  Summarize My Own Text          \u2551");
        System.out.println("\u2551  [0]  Exit                           \u2551");
        System.out.println("\u255A" + "\u2550".repeat(38) + "\u255D");
    }

    /**
     * Runs option 1 where user question and date range are turned into a news summary.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It loops until the user answers no.
     */
    private void handleNewsSearchFlow() {
        boolean continueSearching = true;

        while (continueSearching) {
            try {
                String userQuestion = readLine("What do you want to know about? (e.g. What did Trump say about Iran on 17th April 2026?) ");
                if (userQuestion.isEmpty()) {
                    printErrorBox(
                            "ERROR: The question cannot be empty.",
                            "Please enter what you want to know about."
                    );
                    continue;
                }

                DateRangeSelection dateRangeSelection = askForValidDateRange();
                String optimizedKeywords = geminiService.formatQueryForNews(userQuestion);
                boolean usedGeminiKeywordFallback = isGeminiFallbackResponse(optimizedKeywords);

                if (usedGeminiKeywordFallback) {
                    printErrorBox(
                            "INFO: Gemini keyword extraction is unavailable right now.",
                            "Using direct keywords from your question for this search."
                    );
                }

                String keywordsForNews = usedGeminiKeywordFallback
                        ? buildFallbackKeywords(userQuestion)
                        : optimizedKeywords;

                String combinedArticles = newsApiService.fetchArticles(
                        keywordsForNews,
                        dateRangeSelection.getFromDateAsText(),
                        dateRangeSelection.getToDateAsText()
                );

                if (combinedArticles == null || combinedArticles.trim().isEmpty()) {
                    printErrorBox(
                            "ERROR: No article data could be retrieved.",
                            "Please try again in a few minutes."
                    );
                    continue;
                }

                String summary;
                if ("No articles found for this query and date range.".equals(combinedArticles)) {
                    summary = combinedArticles;
                } else if (usedGeminiKeywordFallback) {
                    summary = buildLocalArticleSummary(combinedArticles);
                } else {
                    summary = geminiService.summarizeArticles(combinedArticles);
                    if (isGeminiFallbackResponse(summary)) {
                        summary = buildLocalArticleSummary(combinedArticles);
                    }
                }

                printSummaryBox(summary);
            } catch (Exception exception) {
                printErrorBox(
                        "ERROR: Something went wrong while searching news.",
                        "Please try again."
                );
            }

            continueSearching = askYesOrNo("Search again? (yes/no): ");
        }
    }

    /**
     * Runs option 2 where the user pastes free text and receives a concise summary.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It loops until the user answers no.
     */
    private void handleTextSummarizerFlow() {
        boolean continueSummarizing = true;

        while (continueSummarizing) {
            try {
                System.out.println("Paste your text below. When done, type END on a new line and press Enter:");
                String userText = readMultilineTextUntilEnd();

                if (userText.isEmpty()) {
                    printErrorBox(
                            "ERROR: No text was provided.",
                            "Please paste your content and type END on a new line."
                    );
                } else {
                    String summary = geminiService.summarizeText(userText);
                    if (isGeminiFallbackResponse(summary)) {
                        summary = buildLocalTextSummary(userText);
                    }
                    printSummaryBox(summary);
                }
            } catch (Exception exception) {
                printErrorBox(
                        "ERROR: Could not summarize your text.",
                        "Please try again."
                );
            }

            continueSummarizing = askYesOrNo("Summarize another? (yes/no): ");
        }
    }

    /**
     * Asks for a date or date range until the user provides a valid value.
     *
     * @param none This method does not receive arguments.
     * @return A valid date range selection for NewsAPI requests.
     */
    private DateRangeSelection askForValidDateRange() {
        while (true) {
            String dateInput = readLine("Enter a date or date range (e.g. 17 April 2026 or 10-17 April 2026). Press Enter to use last 3 weeks as default: ");

            LocalDate today = LocalDate.now();
            if (dateInput.isEmpty()) {
                return new DateRangeSelection(today.minusDays(21), today);
            }

            DateRangeSelection parsedRange = parseDateInput(dateInput);
            if (parsedRange == null) {
                printErrorBox(
                        "ERROR: Could not understand that date format.",
                        "Please use formats like 17 April 2026, April 17 2026, 17/04/2026, or 2026-04-17."
                );
                continue;
            }

            if (isDateRangeInFuture(parsedRange)) {
                printErrorBox(
                        "ERROR: Future dates are not allowed.",
                        "Please enter today or a past date."
                );
                continue;
            }

            if (isDateRangeOlderThanThirtyDays(parsedRange)) {
                showNewsApiDateError();
                continue;
            }

            return parsedRange;
        }
    }

    /**
     * Parses user date text into a from/to date pair.
     *
     * @param rawDateInput The exact date text entered by the user.
     * @return A parsed date range, or null when parsing fails.
     */
    private DateRangeSelection parseDateInput(String rawDateInput) {
        if (rawDateInput == null || rawDateInput.trim().isEmpty()) {
            return null;
        }

        DateRangeSelection compactRange = parseCompactDayRange(rawDateInput);
        if (compactRange != null) {
            return compactRange;
        }

        DateRangeSelection rangeWithToSeparator = parseSeparatedRange(rawDateInput, "(?i)\\s+to\\s+");
        if (rangeWithToSeparator != null) {
            return rangeWithToSeparator;
        }

        DateRangeSelection rangeWithDashSeparator = parseSeparatedRange(rawDateInput, "\\s+-\\s+");
        if (rangeWithDashSeparator != null) {
            return rangeWithDashSeparator;
        }

        try {
            LocalDate singleDate = parseSingleDate(rawDateInput);
            return new DateRangeSelection(singleDate, singleDate);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /**
     * Parses compact ranges like "10-17 April 2026".
     *
     * @param rawDateInput The date text to inspect.
     * @return A parsed range, or null if this pattern does not match.
     */
    private DateRangeSelection parseCompactDayRange(String rawDateInput) {
        Matcher matcher = COMPACT_RANGE_PATTERN.matcher(rawDateInput);
        if (!matcher.matches()) {
            return null;
        }

        try {
            int startDay = Integer.parseInt(matcher.group(1));
            int endDay = Integer.parseInt(matcher.group(2));
            String monthText = matcher.group(3);
            int year = Integer.parseInt(matcher.group(4));

            Month month = Month.from(new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("MMMM")
                    .toFormatter(Locale.ENGLISH)
                    .parse(monthText));

            LocalDate firstDate = LocalDate.of(year, month, startDay);
            LocalDate secondDate = LocalDate.of(year, month, endDay);

            if (firstDate.isAfter(secondDate)) {
                return new DateRangeSelection(secondDate, firstDate);
            }
            return new DateRangeSelection(firstDate, secondDate);
        } catch (DateTimeException | NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Parses ranges separated by words like "to" or symbols like " - ".
     *
     * @param rawDateInput The full text entered by the user.
     * @param separatorRegex The separator pattern to split by.
     * @return A parsed range, or null when this format is not valid.
     */
    private DateRangeSelection parseSeparatedRange(String rawDateInput, String separatorRegex) {
        String[] pieces = rawDateInput.trim().split(separatorRegex);
        if (pieces.length != 2) {
            return null;
        }

        try {
            LocalDate firstDate = parseSingleDate(pieces[0]);
            LocalDate secondDate = parseSingleDate(pieces[1]);

            if (firstDate.isAfter(secondDate)) {
                return new DateRangeSelection(secondDate, firstDate);
            }
            return new DateRangeSelection(firstDate, secondDate);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /**
     * Parses one date using supported formats.
     *
     * @param rawDateValue One date value from user input.
     * @return A parsed LocalDate.
     */
    private LocalDate parseSingleDate(String rawDateValue) {
        String normalizedValue = rawDateValue == null ? "" : rawDateValue.trim();
        normalizedValue = normalizedValue.replace(",", " ").replaceAll("\\s+", " ");

        for (DateTimeFormatter formatter : SUPPORTED_DATE_FORMATS) {
            try {
                return LocalDate.parse(normalizedValue, formatter);
            } catch (DateTimeParseException exception) {
                // Try next format.
            }
        }

        throw new DateTimeParseException("Could not parse date.", normalizedValue, 0);
    }

    /**
     * Checks if the chosen range is older than NewsAPI's 30-day limit.
     *
     * @param dateRangeSelection The chosen date range.
     * @return True if any requested date is older than 30 days.
     */
    private boolean isDateRangeOlderThanThirtyDays(DateRangeSelection dateRangeSelection) {
        LocalDate oldestAllowedDate = LocalDate.now().minusDays(30);
        return dateRangeSelection.fromDate.isBefore(oldestAllowedDate)
                || dateRangeSelection.toDate.isBefore(oldestAllowedDate);
    }

    /**
     * Checks if the selected date range goes into the future.
     *
     * @param dateRangeSelection The chosen date range.
     * @return True when either date is after today.
     */
    private boolean isDateRangeInFuture(DateRangeSelection dateRangeSelection) {
        LocalDate today = LocalDate.now();
        return dateRangeSelection.fromDate.isAfter(today)
                || dateRangeSelection.toDate.isAfter(today);
    }

    /**
     * Prints the exact required out-of-range message for NewsAPI dates.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to the terminal.
     */
    private void showNewsApiDateError() {
        System.out.println("\u2554" + "\u2550".repeat(54) + "\u2557");
        System.out.println("\u2551  ERROR: NewsAPI only supports the last 30 days.      \u2551");
        System.out.println("\u2551  Your requested date is out of range.                \u2551");
        System.out.println("\u2551  Please enter a date within the last 30 days.        \u2551");
        System.out.println("\u255A" + "\u2550".repeat(54) + "\u255D");
    }

    /**
     * Asks a yes or no question and validates the user's answer.
     *
     * @param question The question text to display.
     * @return True for yes, false for no.
     */
    private boolean askYesOrNo(String question) {
        while (true) {
            String answer = readLine(question).toLowerCase(Locale.ENGLISH);
            if ("yes".equals(answer) || "y".equals(answer)) {
                return true;
            }
            if ("no".equals(answer) || "n".equals(answer)) {
                return false;
            }

            printErrorBox(
                    "ERROR: Please answer with yes or no.",
                    "Example valid answers: yes, y, no, n."
            );
        }
    }

    /**
     * Prints a prompt and returns trimmed user input from one line.
     *
     * @param prompt The text to show before reading input.
     * @return Trimmed user input.
     */
    private String readLine(String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine();
        return input == null ? "" : input.trim();
    }

    /**
     * Reads multiple lines until the user types END on a line by itself.
     *
     * @param none This method does not receive arguments.
     * @return The full pasted text joined with line breaks.
     */
    private String readMultilineTextUntilEnd() {
        StringBuilder textBuilder = new StringBuilder();

        while (true) {
            String line = scanner.nextLine();
            if (line != null && "END".equals(line.trim())) {
                break;
            }

            if (textBuilder.length() > 0) {
                textBuilder.append('\n');
            }
            textBuilder.append(line == null ? "" : line);
        }

        return textBuilder.toString().trim();
    }

    /**
     * Prints the summary inside a bordered box and wraps text at 40 characters.
     *
     * @param summaryText The summary text to display.
     * @return Nothing. It prints directly to the terminal.
     */
    private void printSummaryBox(String summaryText) {
        String safeSummaryText = summaryText == null || summaryText.trim().isEmpty()
                ? "Could not complete request. Please try again."
                : summaryText.trim();

        List<String> wrappedLines = wrapText(safeSummaryText, SUMMARY_TEXT_WIDTH);
        int contentWidth = SUMMARY_TEXT_WIDTH + 4;

        System.out.println("\u2554" + "\u2550".repeat(contentWidth) + "\u2557");
        System.out.println("\u2551" + centerText("YOUR SUMMARY", contentWidth) + "\u2551");
        System.out.println("\u2560" + "\u2550".repeat(contentWidth) + "\u2563");
        for (String wrappedLine : wrappedLines) {
            System.out.println("\u2551  " + padRight(wrappedLine, SUMMARY_TEXT_WIDTH) + "  \u2551");
        }
        System.out.println("\u255A" + "\u2550".repeat(contentWidth) + "\u255D");
    }

    /**
     * Wraps text into lines that are at most the requested width.
     *
     * @param text The original text.
     * @param width The maximum width for each line.
     * @return A list of wrapped text lines.
     */
    private List<String> wrapText(String text, int width) {
        List<String> lines = new ArrayList<>();
        String remainingText = text.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();

        if (remainingText.isEmpty()) {
            lines.add("");
            return lines;
        }

        while (remainingText.length() > width) {
            int splitIndex = remainingText.lastIndexOf(' ', width);
            if (splitIndex <= 0) {
                splitIndex = width;
            }

            lines.add(remainingText.substring(0, splitIndex).trim());
            remainingText = remainingText.substring(splitIndex).trim();
        }

        if (!remainingText.isEmpty()) {
            lines.add(remainingText);
        }

        return lines;
    }

    /**
     * Prints one or more error lines in a bordered box.
     *
     * @param messageLines Each entry is one line shown to the user.
     * @return Nothing. It prints directly to the terminal.
     */
    private void printErrorBox(String... messageLines) {
        int contentWidth = 62;
        for (String messageLine : messageLines) {
            if (messageLine != null && messageLine.length() > contentWidth) {
                contentWidth = messageLine.length();
            }
        }

        System.out.println("\u2554" + "\u2550".repeat(contentWidth + 2) + "\u2557");
        for (String messageLine : messageLines) {
            String safeMessageLine = messageLine == null ? "" : messageLine;
            System.out.println("\u2551 " + padRight(safeMessageLine, contentWidth) + " \u2551");
        }
        System.out.println("\u255A" + "\u2550".repeat(contentWidth + 2) + "\u255D");
    }

    /**
     * Centers text inside a fixed-width field.
     *
     * @param value The text to center.
     * @param width The target width.
     * @return Centered text padded with spaces.
     */
    private String centerText(String value, int width) {
        if (value.length() >= width) {
            return value;
        }

        int totalPadding = width - value.length();
        int leftPadding = totalPadding / 2;
        int rightPadding = totalPadding - leftPadding;

        return " ".repeat(leftPadding) + value + " ".repeat(rightPadding);
    }

    /**
     * Pads text with spaces on the right so columns stay aligned.
     *
     * @param value The original text.
     * @param width The target width.
     * @return Right-padded text.
     */
    private String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    /**
     * Checks if a Gemini response contains the common fallback failure message.
     *
     * @param responseText The response text returned from GeminiService.
     * @return True when the response indicates Gemini could not complete the request.
     */
    private boolean isGeminiFallbackResponse(String responseText) {
        if (responseText == null) {
            return true;
        }
        return GEMINI_FALLBACK_RESPONSE.equalsIgnoreCase(responseText.trim());
    }

    /**
     * Builds simple keyword text directly from the user's question when Gemini is unavailable.
     *
     * @param userQuestion The raw natural language question from the user.
     * @return A compact keyword string suitable for NewsAPI search.
     */
    private String buildFallbackKeywords(String userQuestion) {
        String normalized = userQuestion == null ? "" : userQuestion.toLowerCase(Locale.ENGLISH);
        normalized = normalized.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        if (normalized.isEmpty()) {
            return "latest world news";
        }

        Set<String> stopWords = new HashSet<>();
        stopWords.add("what");
        stopWords.add("did");
        stopWords.add("the");
        stopWords.add("a");
        stopWords.add("an");
        stopWords.add("about");
        stopWords.add("on");
        stopWords.add("at");
        stopWords.add("in");
        stopWords.add("to");
        stopWords.add("of");
        stopWords.add("for");
        stopWords.add("say");
        stopWords.add("said");
        stopWords.add("is");
        stopWords.add("are");
        stopWords.add("was");
        stopWords.add("were");
        stopWords.add("and");
        stopWords.add("or");
        stopWords.add("from");
        stopWords.add("with");
        stopWords.add("how");
        stopWords.add("why");
        stopWords.add("when");
        stopWords.add("where");
        stopWords.add("who");

        StringBuilder keywordBuilder = new StringBuilder();
        String[] words = normalized.split(" ");
        int addedCount = 0;

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (stopWords.contains(word)) {
                continue;
            }

            if (word.length() < 3 && !word.matches("\\d+")) {
                continue;
            }

            if (keywordBuilder.length() > 0) {
                keywordBuilder.append(' ');
            }
            keywordBuilder.append(word);
            addedCount++;

            if (addedCount >= 10) {
                break;
            }
        }

        if (keywordBuilder.length() == 0) {
            return normalized;
        }

        return keywordBuilder.toString();
    }

    /**
     * Creates a readable local summary from combined article titles when Gemini is unavailable.
     *
     * @param combinedArticles Combined title and description text from NewsAPI.
     * @return A short paragraph-style summary.
     */
    private String buildLocalArticleSummary(String combinedArticles) {
        String[] lines = combinedArticles.split("\\r?\\n");
        List<String> titles = new ArrayList<>();

        for (String line : lines) {
            if (line.startsWith("Title:")) {
                String title = line.substring("Title:".length()).trim();
                if (!title.isEmpty() && !"N/A".equalsIgnoreCase(title)) {
                    titles.add(title);
                }
            }
            if (titles.size() >= 5) {
                break;
            }
        }

        if (titles.isEmpty()) {
            return "Gemini is currently unavailable. News articles were found, but an AI summary could not be generated right now.";
        }

        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append("Gemini is currently unavailable, so this is a direct quick summary from the latest headlines: ");

        for (int index = 0; index < titles.size(); index++) {
            summaryBuilder.append(titles.get(index));
            if (index < titles.size() - 1) {
                summaryBuilder.append(". ");
            } else {
                summaryBuilder.append('.');
            }
        }

        return summaryBuilder.toString();
    }

    /**
     * Creates a simple local text summary by keeping early sentences to about 30 percent length.
     *
     * @param userText The original user text input.
     * @return A shortened readable summary without calling Gemini.
     */
    private String buildLocalTextSummary(String userText) {
        String normalizedText = userText.replaceAll("\\s+", " ").trim();
        if (normalizedText.isEmpty()) {
            return "Could not complete request. Please try again.";
        }

        int targetLength = Math.max(120, (int) Math.round(normalizedText.length() * 0.30));
        String[] sentences = normalizedText.split("(?<=[.!?])\\s+");

        StringBuilder summaryBuilder = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.isBlank()) {
                continue;
            }

            if (summaryBuilder.length() > 0) {
                summaryBuilder.append(' ');
            }
            summaryBuilder.append(sentence.trim());

            if (summaryBuilder.length() >= targetLength) {
                break;
            }
        }

        if (summaryBuilder.length() == 0) {
            summaryBuilder.append(normalizedText);
        }

        if (summaryBuilder.length() > targetLength + 40) {
            return summaryBuilder.substring(0, targetLength + 40).trim() + "...";
        }

        return summaryBuilder.toString();
    }

    /**
     * Creates supported date parsers for single date inputs.
     *
     * @param none This method does not receive arguments.
     * @return A list of formatters in the order they should be tried.
     */
    private static List<DateTimeFormatter> createDateFormatters() {
        List<DateTimeFormatter> formatters = new ArrayList<>();

        formatters.add(new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d MMMM uuuu").toFormatter(Locale.ENGLISH));
        formatters.add(new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("MMMM d uuuu").toFormatter(Locale.ENGLISH));
        formatters.add(new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/uuuu").toFormatter(Locale.ENGLISH));
        formatters.add(new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("uuuu-M-d").toFormatter(Locale.ENGLISH));

        return formatters;
    }

    /**
     * Holds one validated date range for NewsAPI requests.
     */
    private static final class DateRangeSelection {
        private final LocalDate fromDate;
        private final LocalDate toDate;

        /**
         * Stores from and to dates for one user query.
         *
         * @param fromDate The starting date.
         * @param toDate The ending date.
         * @return A date range container.
         */
        private DateRangeSelection(LocalDate fromDate, LocalDate toDate) {
            this.fromDate = fromDate;
            this.toDate = toDate;
        }

        /**
         * Converts the start date to NewsAPI text format.
         *
         * @param none This method does not receive arguments.
         * @return Start date as yyyy-MM-dd.
         */
        private String getFromDateAsText() {
            return fromDate.format(OUTPUT_DATE_FORMAT);
        }

        /**
         * Converts the end date to NewsAPI text format.
         *
         * @param none This method does not receive arguments.
         * @return End date as yyyy-MM-dd.
         */
        private String getToDateAsText() {
            return toDate.format(OUTPUT_DATE_FORMAT);
        }
    }
}