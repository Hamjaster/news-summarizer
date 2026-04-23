/*
 * This file controls everything the user sees in the terminal.
 * It renders the main menu, handles user input, and coordinates all user-facing flows.
 * It keeps spacing, colors, and summary output consistent across the application.
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

    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

    private static final DateTimeFormatter OUTPUT_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final List<DateTimeFormatter> SUPPORTED_DATE_FORMATS = createDateFormatters();
    private static final String DATE_TOKEN_REGEX = "(?:\\d{1,2}(?:st|nd|rd|th)?\\s+[a-zA-Z]+\\s+\\d{4}|[a-zA-Z]+\\s+\\d{1,2}(?:st|nd|rd|th)?\\s+\\d{4}|\\d{1,2}/\\d{1,2}/\\d{4}|\\d{4}-\\d{1,2}-\\d{1,2})";
    private static final Pattern COMPACT_RANGE_PATTERN = Pattern.compile("(?i)^\\s*(\\d{1,2})\\s*-\\s*(\\d{1,2})\\s+([a-zA-Z]+)\\s+(\\d{4})\\s*$");
    private static final Pattern COMPACT_RANGE_IN_TEXT_PATTERN = Pattern.compile("(?i)\\b\\d{1,2}\\s*-\\s*\\d{1,2}\\s+[a-zA-Z]+\\s+\\d{4}\\b");
    private static final Pattern EXPLICIT_RANGE_IN_TEXT_PATTERN = Pattern.compile("(?i)\\b(" + DATE_TOKEN_REGEX + ")\\b\\s*(?:to|through|until|-)\\s*\\b(" + DATE_TOKEN_REGEX + ")\\b");
    private static final Pattern DATE_TOKEN_IN_TEXT_PATTERN = Pattern.compile("(?i)\\b" + DATE_TOKEN_REGEX + "\\b");
    private static final Pattern LAST_X_DAYS_PATTERN = Pattern.compile("(?i)\\blast\\s+(\\d{1,2})\\s+days\\b");
    private static final String GEMINI_FALLBACK_RESPONSE = "Could not complete request. Please try again.";
    private static final String NO_ARTICLES_MESSAGE = "No articles found for this query and date range.";
    private static final int MAX_INVALID_YES_NO_ATTEMPTS = 3;

    private final Scanner scanner;
    private final GeminiService geminiService;
    private final NewsApiService newsApiService;
    private final MorningBriefing morningBriefing;

    /**
     * Creates the menu UI with all shared services used by user flows.
     *
     * @param none This constructor does not receive arguments.
     * @return A ready MenuUI instance.
     */
    public MenuUI() {
        this.scanner = new Scanner(System.in);
        this.geminiService = new GeminiService();
        this.newsApiService = new NewsApiService();
        this.morningBriefing = new MorningBriefing(scanner, geminiService, newsApiService);
    }

    /**
     * Starts the main menu loop and routes all top-level options.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It exits only when user chooses option 0.
     */
    public void start() {
        boolean keepRunning = true;

        try {
            showStartupSplash();

            while (keepRunning) {
                showMainMenu();
                String menuChoice = readLine("Your choice:");

                if ("1".equals(menuChoice)) {
                    handleNewsSearchFlow();
                } else if ("2".equals(menuChoice)) {
                    handleTextSummarizerFlow();
                } else if ("3".equals(menuChoice)) {
                    handleContentAnalyzerFlow();
                } else if ("5".equals(menuChoice)) {
                    handleMorningBriefingFlow();
                } else if ("0".equals(menuChoice)) {
                    keepRunning = false;
                    printSuccessMessage("Goodbye.");
                } else {
                    printErrorBox(
                            "ERROR: Invalid menu option.",
                            "Please choose 1, 2, 3, 5, or 0."
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
     * Shows the startup splash screen, then pauses briefly before menu display.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to terminal.
     */
    private void showStartupSplash() {
        TerminalUtils.printCenteredLine("═".repeat(TerminalUtils.BOX_WIDTH), CYAN + BOLD, RESET);
        TerminalUtils.printCenteredLine("WELCOME", CYAN + BOLD, RESET);
        TerminalUtils.printCenteredLine("NEWS & SUMMARY ASSISTANT", CYAN + BOLD, RESET);
        TerminalUtils.printCenteredLine("═".repeat(TerminalUtils.BOX_WIDTH), CYAN + BOLD, RESET);
        TerminalUtils.pause(1500);
    }

    /**
     * Prints the main menu using the shared 80-character centered style.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to terminal.
     */
    private void showMainMenu() {
        List<String> menuLines = new ArrayList<>();
        menuLines.add("[1]  Search & Summarize News");
        menuLines.add("[2]  Summarize My Own Text");
        menuLines.add("[3]  Analyze Content");
        menuLines.add("[5]  Morning Briefing");
        menuLines.add("[0]  Exit");

        TerminalUtils.printTitledBox(
                "NEWS & SUMMARY ASSISTANT",
                menuLines,
                CYAN + BOLD,
                CYAN + BOLD,
                WHITE,
                RESET
        );
    }

    /**
     * Handles option 1 news flow with automatic date extraction from question text.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It loops until user answers no.
     */
    private void handleNewsSearchFlow() {
        boolean continueSearching = true;

        while (continueSearching) {
            try {
                String userQuestion = readLine("What do you want to know about");
                if (userQuestion.isEmpty()) {
                    printErrorBox(
                            "ERROR: The question cannot be empty.",
                            "Please enter what you want to know about."
                    );
                    continue;
                }

                DateRangeSelection dateRangeSelection = deriveDateRangeFromQuestion(userQuestion);
                if (dateRangeSelection == null) {
                    continue;
                }

                String keywordSourceText = sanitizeQueryForKeywordExtraction(userQuestion);
                String optimizedKeywords = geminiService.formatQueryForNews(keywordSourceText);
                boolean usedGeminiKeywordFallback = isGeminiFallbackResponse(optimizedKeywords);

                if (usedGeminiKeywordFallback) {
                    printGeminiUnavailableNotice(
                            "keyword extraction",
                            "Using direct keywords from your question for this search."
                    );
                }

                String keywordsForNews = usedGeminiKeywordFallback
                        ? buildFallbackKeywords(keywordSourceText)
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
                if (NO_ARTICLES_MESSAGE.equals(combinedArticles)) {
                    summary = combinedArticles;
                } else if (usedGeminiKeywordFallback) {
                    summary = buildLocalArticleSummary(combinedArticles);
                } else {
                    summary = geminiService.summarizeArticles(combinedArticles);
                    if (isGeminiFallbackResponse(summary)) {
                        printGeminiUnavailableNotice(
                                "article summarization",
                                "Showing a local quick summary from headlines instead."
                        );
                        summary = buildLocalArticleSummary(combinedArticles);
                    }
                }

                printSummaryOutput(combinedArticles, summary);
            } catch (Exception exception) {
                printErrorBox(
                        "ERROR: Something went wrong while searching news.",
                        "Please try again."
                );
            }

            continueSearching = askYesOrNo("Search again (yes/no)");
        }
    }

    /**
     * Handles option 2 user text summarization using double-Enter multiline input.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It loops until user answers no.
     */
    private void handleTextSummarizerFlow() {
        boolean continueSummarizing = true;

        while (continueSummarizing) {
            try {
                TerminalUtils.printCenteredLine("Paste your text below. Press Enter twice when done:", WHITE, RESET);
                String userText = readMultilineTextUntilDoubleEnter();

                if (userText.isEmpty()) {
                    printErrorBox(
                            "ERROR: No text was provided.",
                            "Paste your text and press Enter twice when done."
                    );
                } else {
                    String summary = geminiService.summarizeText(userText);
                    if (isGeminiFallbackResponse(summary)) {
                        printGeminiUnavailableNotice(
                                "text summarization",
                                "Showing a local quick summary instead."
                        );
                        summary = buildLocalTextSummary(userText);
                    }

                    printSummaryOutput(userText, summary);
                }
            } catch (Exception exception) {
                printErrorBox(
                        "ERROR: Could not summarize your text.",
                        "Please try again."
                );
            }

            continueSummarizing = askYesOrNo("Summarize another (yes/no)");
        }
    }

    /**
     * Opens the content analyzer flow from option 3.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. Control returns to main menu when analyzer exits.
     */
    private void handleContentAnalyzerFlow() {
        try {
            ContentAnalyzerUI contentAnalyzerUI = new ContentAnalyzerUI(scanner, geminiService);
            contentAnalyzerUI.start();
        } catch (Exception exception) {
            printErrorBox(
                    "ERROR: Could not open the content analyzer.",
                    "Please try again."
            );
        }
    }

    /**
     * Runs the new Morning Briefing flow from option 5.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It returns to main menu after briefing flow ends.
     */
    private void handleMorningBriefingFlow() {
        try {
            morningBriefing.start();
        } catch (Exception exception) {
            printErrorBox(
                    "ERROR: Morning Briefing could not start.",
                    "Please try again."
            );
        }
    }

    /**
     * Derives date range from question text, defaulting to last 3 weeks when absent.
     *
     * @param userQuestion User question text.
     * @return Validated date range selection or null on invalid ranges.
     */
    private DateRangeSelection deriveDateRangeFromQuestion(String userQuestion) {
        DateRangeSelection extractedRange = extractDateRangeFromQuestion(userQuestion);
        if (extractedRange == null) {
            LocalDate today = LocalDate.now();
            return new DateRangeSelection(today.minusDays(21), today);
        }

        if (isDateRangeInFuture(extractedRange)) {
            printErrorBox(
                    "ERROR: Future dates are not allowed.",
                    "Please ask with today or a past date in your question."
            );
            return null;
        }

        if (isDateRangeOlderThanThirtyDays(extractedRange)) {
            showNewsApiDateError();
            return null;
        }

        return extractedRange;
    }

    /**
     * Extracts explicit or relative dates directly from natural-language question text.
     *
     * @param userQuestion User question text.
     * @return Parsed date range or null when no date expression is detected.
     */
    private DateRangeSelection extractDateRangeFromQuestion(String userQuestion) {
        if (userQuestion == null || userQuestion.isBlank()) {
            return null;
        }

        DateRangeSelection relativeRange = extractRelativeDateRange(userQuestion);
        if (relativeRange != null) {
            return relativeRange;
        }

        String normalizedQuestion = userQuestion
                .replace(",", " ")
                .replaceAll("\\s+", " ")
                .trim();

        Matcher compactMatcher = COMPACT_RANGE_IN_TEXT_PATTERN.matcher(normalizedQuestion);
        if (compactMatcher.find()) {
            DateRangeSelection parsedCompactRange = parseDateInput(compactMatcher.group());
            if (parsedCompactRange != null) {
                return parsedCompactRange;
            }
        }

        Matcher explicitRangeMatcher = EXPLICIT_RANGE_IN_TEXT_PATTERN.matcher(normalizedQuestion);
        if (explicitRangeMatcher.find()) {
            String rangeText = explicitRangeMatcher.group(1) + " to " + explicitRangeMatcher.group(2);
            DateRangeSelection parsedExplicitRange = parseDateInput(rangeText);
            if (parsedExplicitRange != null) {
                return parsedExplicitRange;
            }
        }

        List<LocalDate> detectedDates = new ArrayList<>();
        Matcher dateTokenMatcher = DATE_TOKEN_IN_TEXT_PATTERN.matcher(normalizedQuestion);
        while (dateTokenMatcher.find()) {
            String dateToken = dateTokenMatcher.group();
            try {
                LocalDate parsedDate = parseSingleDate(dateToken);
                if (!detectedDates.contains(parsedDate)) {
                    detectedDates.add(parsedDate);
                }
            } catch (DateTimeParseException exception) {
                // Ignore unmatched fragments and keep scanning.
            }

            if (detectedDates.size() >= 2) {
                break;
            }
        }

        if (detectedDates.isEmpty()) {
            return null;
        }

        if (detectedDates.size() == 1) {
            return new DateRangeSelection(detectedDates.get(0), detectedDates.get(0));
        }

        return toDateRange(detectedDates.get(0), detectedDates.get(1));
    }

    /**
     * Extracts relative ranges like today, yesterday, last week, or last X days.
     *
     * @param userQuestion User question text.
     * @return Derived relative date range or null when not present.
     */
    private DateRangeSelection extractRelativeDateRange(String userQuestion) {
        String lowerText = userQuestion.toLowerCase(Locale.ENGLISH);
        LocalDate today = LocalDate.now();

        Matcher lastDaysMatcher = LAST_X_DAYS_PATTERN.matcher(lowerText);
        if (lastDaysMatcher.find()) {
            try {
                int requestedDays = Integer.parseInt(lastDaysMatcher.group(1));
                int boundedDays = Math.max(1, Math.min(requestedDays, 30));
                return new DateRangeSelection(today.minusDays(boundedDays - 1L), today);
            } catch (NumberFormatException exception) {
                // Ignore invalid relative day counts.
            }
        }

        if (lowerText.contains("last week")) {
            return new DateRangeSelection(today.minusDays(7), today);
        }

        if (lowerText.contains("yesterday")) {
            LocalDate yesterday = today.minusDays(1);
            return new DateRangeSelection(yesterday, yesterday);
        }

        if (lowerText.contains("today")) {
            return new DateRangeSelection(today, today);
        }

        return null;
    }

    /**
     * Reorders two dates into ascending order when needed.
     *
     * @param firstDate First date candidate.
     * @param secondDate Second date candidate.
     * @return Valid normalized date range.
     */
    private DateRangeSelection toDateRange(LocalDate firstDate, LocalDate secondDate) {
        if (firstDate.isAfter(secondDate)) {
            return new DateRangeSelection(secondDate, firstDate);
        }
        return new DateRangeSelection(firstDate, secondDate);
    }

    /**
     * Removes date expressions from question before keyword extraction.
     *
     * @param userQuestion Raw question text.
     * @return Sanitized keyword-friendly question text.
     */
    private String sanitizeQueryForKeywordExtraction(String userQuestion) {
        String sanitized = userQuestion == null ? "" : userQuestion;
        sanitized = COMPACT_RANGE_IN_TEXT_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = EXPLICIT_RANGE_IN_TEXT_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = DATE_TOKEN_IN_TEXT_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = LAST_X_DAYS_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = sanitized.replaceAll("(?i)\\btoday\\b|\\byesterday\\b|\\blast\\s+week\\b", " ");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();

        if (sanitized.isEmpty()) {
            return userQuestion == null ? "" : userQuestion.trim();
        }
        return sanitized;
    }

    /**
     * Parses one raw date input into either single-day or range selection.
     *
     * @param rawDateInput Raw date text.
     * @return Parsed date range selection or null when parsing fails.
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
     * Parses compact ranges such as 10-17 April 2026.
     *
     * @param rawDateInput Raw date text.
     * @return Parsed range or null when format does not match.
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
            return toDateRange(firstDate, secondDate);
        } catch (DateTimeException | NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Parses ranges split by custom separator regex.
     *
     * @param rawDateInput Raw date range text.
     * @param separatorRegex Separator pattern used for split.
     * @return Parsed range or null when invalid.
     */
    private DateRangeSelection parseSeparatedRange(String rawDateInput, String separatorRegex) {
        String[] pieces = rawDateInput.trim().split(separatorRegex);
        if (pieces.length != 2) {
            return null;
        }

        try {
            LocalDate firstDate = parseSingleDate(pieces[0]);
            LocalDate secondDate = parseSingleDate(pieces[1]);
            return toDateRange(firstDate, secondDate);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /**
     * Parses one date value using all supported date formatter patterns.
     *
     * @param rawDateValue One date fragment.
     * @return Parsed LocalDate.
     */
    private LocalDate parseSingleDate(String rawDateValue) {
        String normalizedValue = rawDateValue == null ? "" : rawDateValue.trim();
        normalizedValue = normalizedValue
                .replace(",", " ")
                .replaceAll("(?i)\\b(\\d{1,2})(st|nd|rd|th)\\b", "$1")
                .replaceAll("\\s+", " ");

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
     * Validates whether selected range violates NewsAPI 30-day historical limit.
     *
     * @param dateRangeSelection Date range selection.
     * @return True when selected range is too old.
     */
    private boolean isDateRangeOlderThanThirtyDays(DateRangeSelection dateRangeSelection) {
        LocalDate oldestAllowedDate = LocalDate.now().minusDays(30);
        return dateRangeSelection.fromDate.isBefore(oldestAllowedDate)
                || dateRangeSelection.toDate.isBefore(oldestAllowedDate);
    }

    /**
     * Validates whether selected range includes future dates.
     *
     * @param dateRangeSelection Date range selection.
     * @return True when range includes future dates.
     */
    private boolean isDateRangeInFuture(DateRangeSelection dateRangeSelection) {
        LocalDate today = LocalDate.now();
        return dateRangeSelection.fromDate.isAfter(today)
                || dateRangeSelection.toDate.isAfter(today);
    }

    /**
     * Prints out-of-range NewsAPI date guidance in styled error format.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to terminal.
     */
    private void showNewsApiDateError() {
        printErrorBox(
                "ERROR: NewsAPI only supports the last 30 days.",
                "Your requested date is out of range.",
                "Please use a date within the last 30 days."
        );
    }

    /**
     * Prompts for yes/no input with validation.
     *
     * @param question Prompt question.
     * @return True for yes, false for no.
     */
    private boolean askYesOrNo(String question) {
        int invalidAttempts = 0;

        while (true) {
            String answer = readLine(question).toLowerCase(Locale.ENGLISH);
            if (answer.isBlank()) {
                return false;
            }

            if ("yes".equals(answer) || "y".equals(answer)) {
                return true;
            }
            if ("no".equals(answer) || "n".equals(answer)) {
                return false;
            }

            invalidAttempts++;
            if (invalidAttempts >= MAX_INVALID_YES_NO_ATTEMPTS) {
                printErrorBox(
                        "ERROR: Too many invalid yes/no answers.",
                        "Returning to the previous menu."
                );
                return false;
            }

            printErrorBox(
                    "ERROR: Please answer with yes or no.",
                    "Valid answers: yes, y, no, n.",
                    "Press Enter without typing to choose no."
            );
        }
    }

    /**
     * Reads one line from terminal using styled centered prompt output.
     *
     * @param prompt Prompt label text.
     * @return Trimmed input text.
     */
    private String readLine(String prompt) {
        return TerminalUtils.prompt(scanner, prompt, CYAN, WHITE, RESET);
    }

    /**
     * Reads multiline text until two consecutive empty lines are entered.
     *
     * @param none This method does not receive arguments.
     * @return Trimmed multiline text.
     */
    private String readMultilineTextUntilDoubleEnter() {
        StringBuilder textBuilder = new StringBuilder();
        int emptyLineCount = 0;

        while (true) {
            String line = scanner.nextLine();
            String safeLine = line == null ? "" : line;

            if (safeLine.trim().isEmpty()) {
                emptyLineCount++;
                if (emptyLineCount >= 2) {
                    break;
                }
                if (textBuilder.length() > 0) {
                    textBuilder.append('\n');
                }
            } else {
                emptyLineCount = 0;
                if (textBuilder.length() > 0) {
                    textBuilder.append('\n');
                }
                textBuilder.append(safeLine);
            }
        }

        return textBuilder.toString().trim();
    }

    /**
     * Prints summary box and all required post-summary extras.
     *
     * @param originalText Original text used to create summary.
     * @param summaryText Final summary text shown to user.
     * @return Nothing. It prints directly to terminal.
     */
    private void printSummaryOutput(String originalText, String summaryText) {
        TerminalUtils.printSummaryBox(
                "YOUR SUMMARY",
                summaryText,
                CYAN + BOLD,
                CYAN + BOLD,
                WHITE,
                RESET
        );

        TerminalUtils.printWordStats(originalText, summaryText, YELLOW, RESET);
        TerminalUtils.printGreenDivider(GREEN, RESET);
    }

    /**
     * Prints standardized Gemini-unavailable info with fallback details.
     *
     * @param failedStep Failed Gemini capability description.
     * @param fallbackAction Local fallback action description.
     * @return Nothing. It prints directly to terminal.
     */
    private void printGeminiUnavailableNotice(String failedStep, String fallbackAction) {
        List<String> infoLines = new ArrayList<>();
        infoLines.add("INFO: Gemini " + failedStep + " is unavailable right now.");
        infoLines.add(fallbackAction);

        String reason = geminiService.getLastFailureReason();
        if (reason != null && !reason.isBlank()) {
            infoLines.add("Details: " + reason);
        }

        printInfoBox(infoLines.toArray(new String[0]));
    }

    /**
     * Checks if text equals the standard Gemini fallback response.
     *
     * @param responseText Response text to inspect.
     * @return True when fallback text is detected.
     */
    private boolean isGeminiFallbackResponse(String responseText) {
        if (responseText == null) {
            return true;
        }
        return GEMINI_FALLBACK_RESPONSE.equalsIgnoreCase(responseText.trim());
    }

    /**
     * Prints a red styled bordered error box.
     *
     * @param messageLines Error lines.
     * @return Nothing. It prints directly to terminal.
     */
    private void printErrorBox(String... messageLines) {
        List<String> lines = new ArrayList<>();
        if (messageLines != null) {
            for (String messageLine : messageLines) {
                lines.add(messageLine == null ? "" : messageLine);
            }
        }
        TerminalUtils.printSimpleBox(lines, RED + BOLD, RED, RESET);
    }

    /**
     * Prints a yellow styled bordered informational box.
     *
     * @param messageLines Info lines.
     * @return Nothing. It prints directly to terminal.
     */
    private void printInfoBox(String... messageLines) {
        List<String> lines = new ArrayList<>();
        if (messageLines != null) {
            for (String messageLine : messageLines) {
                lines.add(messageLine == null ? "" : messageLine);
            }
        }
        TerminalUtils.printSimpleBox(lines, YELLOW + BOLD, YELLOW, RESET);
    }

    /**
     * Prints a centered green success message line.
     *
     * @param message Success message text.
     * @return Nothing. It prints directly to terminal.
     */
    private void printSuccessMessage(String message) {
        TerminalUtils.printCenteredLine(message, GREEN + BOLD, RESET);
    }

    /**
     * Builds fallback search keywords locally when Gemini keyword extraction fails.
     *
     * @param userQuestion User question text.
     * @return Compact keyword string.
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
        int addedCount = 0;
        for (String word : normalized.split(" ")) {
            if (word.isEmpty() || stopWords.contains(word)) {
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
     * Builds local fallback summary from headline titles when Gemini is unavailable.
     *
     * @param combinedArticles Combined article text.
     * @return Readable local summary.
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
            return "Gemini is currently unavailable. News articles were found, but AI summary is unavailable right now.";
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
     * Builds local fallback summary from user text when Gemini is unavailable.
     *
     * @param userText Raw user text.
     * @return Local compressed summary text.
     */
    private String buildLocalTextSummary(String userText) {
        String normalizedText = userText.replaceAll("\\s+", " ").trim();
        if (normalizedText.isEmpty()) {
            return GEMINI_FALLBACK_RESPONSE;
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
     * Creates supported date formatters for parsing single-date expressions.
     *
     * @param none This method does not receive arguments.
     * @return Supported formatter list.
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
     * Container for one validated NewsAPI date range selection.
     */
    private static final class DateRangeSelection {
        private final LocalDate fromDate;
        private final LocalDate toDate;

        /**
         * Creates one immutable date-range value object.
         *
         * @param fromDate Start date.
         * @param toDate End date.
         * @return A date-range container.
         */
        private DateRangeSelection(LocalDate fromDate, LocalDate toDate) {
            this.fromDate = fromDate;
            this.toDate = toDate;
        }

        /**
         * Converts start date to NewsAPI text format.
         *
         * @param none This method does not receive arguments.
         * @return Start date as yyyy-MM-dd.
         */
        private String getFromDateAsText() {
            return fromDate.format(OUTPUT_DATE_FORMAT);
        }

        /**
         * Converts end date to NewsAPI text format.
         *
         * @param none This method does not receive arguments.
         * @return End date as yyyy-MM-dd.
         */
        private String getToDateAsText() {
            return toDate.format(OUTPUT_DATE_FORMAT);
        }
    }
}