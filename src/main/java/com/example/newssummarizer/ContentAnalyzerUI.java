/*
 * This file controls the user experience for the content analyzer feature.
 * It asks the user what type of content they have, takes their input,
 * creates the right analyzer, and shows results.
 */
package com.example.newssummarizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class ContentAnalyzerUI {

    private static final int SUMMARY_TEXT_WIDTH = 40;

    private final Scanner scanner;
    private final GeminiService geminiService;

    /**
     * Creates the UI controller for the content analyzer feature.
     *
     * @param scanner Shared scanner from the main menu flow.
     * @param geminiService Shared Gemini service used by all analyzers.
     * @return A ready content analyzer UI.
     */
    public ContentAnalyzerUI(Scanner scanner, GeminiService geminiService) {
        this.scanner = scanner;
        this.geminiService = geminiService;
    }

    /**
     * Starts the content analyzer menu loop and handles user choices.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It returns control to the main menu when done.
     */
    public void start() {
        boolean keepAnalyzing = true;

        while (keepAnalyzing) {
            try {
                ContentAnalyzer selectedAnalyzer = askForAnalyzerType();
                if (selectedAnalyzer == null) {
                    return;
                }

                String userContent = readContentInput(selectedAnalyzer.getTypeName());
                if (userContent.isEmpty()) {
                    printErrorBox(
                            "ERROR: No content was provided.",
                            "Please paste your content and type END on a new line."
                    );
                    keepAnalyzing = askYesOrNo("Analyze something else? (yes/no): ");
                    continue;
                }

                boolean backToTypeSelection = false;
                while (!backToTypeSelection) {
                    selectedAnalyzer.printAnalysisMenu();
                    String analysisChoice = readLine("Choose an option: ");

                    if ("0".equals(analysisChoice)) {
                        backToTypeSelection = true;
                        continue;
                    }

                    printWorkingMessage();
                    String analysisResult = runSelectedAnalysis(selectedAnalyzer, analysisChoice, userContent);
                    if (analysisResult == null) {
                        continue;
                    }

                    printSummaryBox(analysisResult);

                    String postResultChoice = askPostResultChoice();
                    if ("1".equals(postResultChoice)) {
                        continue;
                    }
                    if ("2".equals(postResultChoice)) {
                        backToTypeSelection = true;
                        continue;
                    }

                    if ("0".equals(postResultChoice)) {
                        keepAnalyzing = false;
                        return;
                    }
                }
            } catch (Exception exception) {
                printErrorBox(
                        "ERROR: The content analyzer encountered an unexpected problem.",
                        "Please try again."
                );
                keepAnalyzing = askYesOrNo("Analyze something else? (yes/no): ");
            }
        }
    }

    /**
     * Prints a small loading status so users know analysis is in progress.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to the terminal.
     */
    private void printWorkingMessage() {
        printInfoBox(
                "Loading...",
                "Working on your analysis. Please wait."
        );
    }

    /**
     * Asks what to do after showing one analysis result.
     *
     * @param none This method does not receive arguments.
     * @return One of: 1 back to current analyzer menu, 2 new content flow, 0 main menu.
     */
    private String askPostResultChoice() {
        while (true) {
            showPostResultMenu();
            String postResultChoice = readLine("Choose an option: ");

            if ("1".equals(postResultChoice) || "2".equals(postResultChoice) || "0".equals(postResultChoice)) {
                return postResultChoice;
            }

            printErrorBox(
                    "ERROR: Invalid option.",
                    "Please choose 1, 2, or 0."
            );
        }
    }

    /**
     * Prints the post-result navigation menu.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to the terminal.
     */
    private void showPostResultMenu() {
        System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        System.out.println("\u2551          WHAT NEXT?                 \u2551");
        System.out.println("\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563");
        System.out.println("\u2551  [1]  Back to analyzer menu         \u2551");
        System.out.println("\u2551  [2]  Analyze new content           \u2551");
        System.out.println("\u2551  [0]  Back to Main Menu            \u2551");
        System.out.println("\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D");
    }

    /**
     * Shows type options and returns the selected analyzer implementation.
     *
     * @param none This method does not receive arguments.
     * @return A concrete analyzer instance, or null to go back to main menu.
     */
    private ContentAnalyzer askForAnalyzerType() {
        while (true) {
            showTypeSelectionMenu();
            String typeChoice = readLine("Choose an option: ");

            if ("1".equals(typeChoice)) {
                return new LyricsAnalyzer(geminiService);
            }
            if ("2".equals(typeChoice)) {
                return new BookAnalyzer(geminiService);
            }
            if ("3".equals(typeChoice)) {
                return new NewspaperAnalyzer(geminiService);
            }
            if ("0".equals(typeChoice)) {
                return null;
            }

            printErrorBox(
                    "ERROR: Invalid type option.",
                    "Please choose 1, 2, 3, or 0."
            );
        }
    }

    /**
     * Prints the content type selection menu.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to the terminal.
     */
    private void showTypeSelectionMenu() {
        System.out.println("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        System.out.println("\u2551        WHAT ARE YOU ANALYZING?       \u2551");
        System.out.println("\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563");
        System.out.println("\u2551  [1]  Song Lyrics                    \u2551");
        System.out.println("\u2551  [2]  Book Excerpt                   \u2551");
        System.out.println("\u2551  [3]  Newspaper Article              \u2551");
        System.out.println("\u2551  [0]  Back to Main Menu              \u2551");
        System.out.println("\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D");
    }

    /**
     * Collects multiline content input from the user until END is entered.
     *
     * @param typeName Display name of the selected content type.
     * @return Full input text joined with line breaks.
     */
    private String readContentInput(String typeName) {
        System.out.println("Paste your " + typeName + " below. When done, type END on a new line and press Enter:");
        return readMultilineTextUntilEnd();
    }

    /**
     * Executes the selected analyzer action based on content type and menu choice.
     *
     * @param analyzer Selected analyzer implementation.
     * @param analysisChoice Chosen action from the analyzer menu.
     * @param contentText Full user content to analyze.
     * @return Analysis result text, or null when choice is invalid.
     */
    private String runSelectedAnalysis(ContentAnalyzer analyzer, String analysisChoice, String contentText) {
        try {
            if (analyzer instanceof LyricsAnalyzer) {
                LyricsAnalyzer lyricsAnalyzer = (LyricsAnalyzer) analyzer;
                if ("1".equals(analysisChoice)) {
                    return safeResult(lyricsAnalyzer.analyze(contentText));
                }
                if ("2".equals(analysisChoice)) {
                    return safeResult(lyricsAnalyzer.getArtistInsights(contentText));
                }
                if ("3".equals(analysisChoice)) {
                    return safeResult(lyricsAnalyzer.suggestCreativeUses(contentText));
                }
                printErrorBox("ERROR: Invalid lyrics analysis option.", "Please choose 1, 2, 3, or 0.");
                return null;
            }

            if (analyzer instanceof BookAnalyzer) {
                BookAnalyzer bookAnalyzer = (BookAnalyzer) analyzer;
                if ("1".equals(analysisChoice)) {
                    return safeResult(bookAnalyzer.analyze(contentText));
                }
                if ("2".equals(analysisChoice)) {
                    return safeResult(bookAnalyzer.getThemes(contentText));
                }
                printErrorBox("ERROR: Invalid book analysis option.", "Please choose 1, 2, or 0.");
                return null;
            }

            if (analyzer instanceof NewspaperAnalyzer) {
                NewspaperAnalyzer newspaperAnalyzer = (NewspaperAnalyzer) analyzer;
                if ("1".equals(analysisChoice)) {
                    return safeResult(newspaperAnalyzer.analyze(contentText));
                }
                if ("2".equals(analysisChoice)) {
                    return safeResult(newspaperAnalyzer.getKeyFacts(contentText));
                }
                printErrorBox("ERROR: Invalid newspaper analysis option.", "Please choose 1, 2, or 0.");
                return null;
            }

            return safeResult(analyzer.analyze(contentText));
        } catch (Exception exception) {
            printErrorBox(
                    "ERROR: Analysis failed for this content.",
                    "Please try again."
            );
            return "Could not complete request. Please try again.";
        }
    }

    /**
     * Asks a yes or no question and validates the answer.
     *
     * @param question Prompt shown to the user.
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
     * Prints a prompt and reads one trimmed line from terminal input.
     *
     * @param prompt Prompt text to display.
     * @return Trimmed user input, or empty text when null.
     */
    private String readLine(String prompt) {
        System.out.print(prompt);
        String input = scanner.nextLine();
        return input == null ? "" : input.trim();
    }

    /**
     * Reads multiline text until END appears by itself on one line.
     *
     * @param none This method does not receive arguments.
     * @return Trimmed multiline text.
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
     * Prints analysis output inside the standard bordered summary box.
     *
     * @param summaryText Result text to display.
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
     * Prints one or more error lines in a bordered box with wrapped text.
     *
     * @param messageLines Error text lines to show.
     * @return Nothing. It prints directly to the terminal.
     */
    private void printErrorBox(String... messageLines) {
        int contentWidth = 62;
        List<String> wrappedErrorLines = new ArrayList<>();

        for (String messageLine : messageLines) {
            String safeLine = messageLine == null ? "" : messageLine;
            wrappedErrorLines.addAll(wrapText(safeLine, contentWidth));
        }

        System.out.println("\u2554" + "\u2550".repeat(contentWidth + 2) + "\u2557");
        for (String wrappedErrorLine : wrappedErrorLines) {
            System.out.println("\u2551 " + padRight(wrappedErrorLine, contentWidth) + " \u2551");
        }
        System.out.println("\u255A" + "\u2550".repeat(contentWidth + 2) + "\u255D");
    }

    /**
     * Prints one or more informational lines in a bordered box with wrapped text.
     *
     * @param messageLines Info text lines to show.
     * @return Nothing. It prints directly to the terminal.
     */
    private void printInfoBox(String... messageLines) {
        int contentWidth = 62;
        List<String> wrappedInfoLines = new ArrayList<>();

        for (String messageLine : messageLines) {
            String safeLine = messageLine == null ? "" : messageLine;
            wrappedInfoLines.addAll(wrapText(safeLine, contentWidth));
        }

        System.out.println("\u2554" + "\u2550".repeat(contentWidth + 2) + "\u2557");
        for (String wrappedInfoLine : wrappedInfoLines) {
            System.out.println("\u2551 " + padRight(wrappedInfoLine, contentWidth) + " \u2551");
        }
        System.out.println("\u255A" + "\u2550".repeat(contentWidth + 2) + "\u255D");
    }

    /**
     * Wraps text so every line stays inside the selected width.
     *
     * @param text Raw text to wrap.
     * @param width Max width for each wrapped line.
     * @return List of wrapped lines.
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
     * Centers text inside a fixed width area.
     *
     * @param value Text to center.
     * @param width Width of the target field.
     * @return Centered text with space padding.
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
     * Right-pads text so terminal box columns stay aligned.
     *
     * @param value Original text.
     * @param width Target width.
     * @return Right-padded text.
     */
    private String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    /**
     * Normalizes empty Gemini outputs into one safe fallback message.
     *
     * @param result Raw result from analyzer methods.
     * @return Safe displayable result text.
     */
    private String safeResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            return "Could not complete request. Please try again.";
        }
        return result.trim();
    }
}
