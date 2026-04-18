/*
 * This file controls the user experience for the content analyzer feature.
 * It asks the user what type of content they want to analyze, collects input,
 * executes analysis actions, and prints styled summaries with extra metadata.
 */
package com.example.newssummarizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class ContentAnalyzerUI {

    private static final String CYAN = "\u001B[36m";
    private static final String WHITE = "\u001B[37m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String GEMINI_FALLBACK_RESPONSE = "Could not complete request. Please try again.";

    private final Scanner scanner;
    private final GeminiService geminiService;

    /**
     * Creates the content analyzer UI controller.
     *
     * @param scanner Shared scanner from the main menu flow.
     * @param geminiService Shared Gemini service used by analyzer methods.
     * @return A ready ContentAnalyzerUI instance.
     */
    public ContentAnalyzerUI(Scanner scanner, GeminiService geminiService) {
        this.scanner = scanner;
        this.geminiService = geminiService;
    }

    /**
     * Starts the content analyzer loop and keeps state transitions clear.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It returns to main menu when flow ends.
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
                            "Paste your text and press Enter twice when done."
                    );
                    keepAnalyzing = askYesOrNo("Analyze something else? (yes/no)");
                    continue;
                }

                boolean backToTypeSelection = false;
                while (!backToTypeSelection) {
                    selectedAnalyzer.printAnalysisMenu();
                    String analysisChoice = readLine("Your choice:");

                    if ("0".equals(analysisChoice)) {
                        backToTypeSelection = true;
                        continue;
                    }

                    String analysisResult = runSelectedAnalysis(selectedAnalyzer, analysisChoice, userContent);
                    if (analysisResult == null) {
                        continue;
                    }

                    printSummaryOutput(userContent, analysisResult);

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
                keepAnalyzing = askYesOrNo("Analyze something else? (yes/no)");
            }
        }
    }

    /**
     * Asks what to do after one analysis result is printed.
     *
     * @param none This method does not receive arguments.
     * @return 1 for same analyzer menu, 2 for new content, 0 for main menu.
     */
    private String askPostResultChoice() {
        while (true) {
            showPostResultMenu();
            String postResultChoice = readLine("Your choice:");

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
     * @return Nothing. It prints directly to terminal.
     */
    private void showPostResultMenu() {
        List<String> lines = new ArrayList<>();
        lines.add("[1]  Back to analyzer menu");
        lines.add("[2]  Analyze new content");
        lines.add("[0]  Back to Main Menu");

        TerminalUtils.printTitledBox(
                "WHAT NEXT?",
                lines,
                CYAN + BOLD,
                CYAN + BOLD,
                WHITE,
                RESET
        );
    }

    /**
     * Shows type choices and returns selected analyzer implementation.
     *
     * @param none This method does not receive arguments.
     * @return Concrete analyzer or null when user goes back.
     */
    private ContentAnalyzer askForAnalyzerType() {
        while (true) {
            showTypeSelectionMenu();
            String typeChoice = readLine("Your choice:");

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
     * Prints the analyzer type selection menu.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It prints directly to terminal.
     */
    private void showTypeSelectionMenu() {
        List<String> lines = new ArrayList<>();
        lines.add("[1]  Song Lyrics");
        lines.add("[2]  Book Excerpt");
        lines.add("[3]  Newspaper Article");
        lines.add("[0]  Back to Main Menu");

        TerminalUtils.printTitledBox(
                "WHAT ARE YOU ANALYZING?",
                lines,
                CYAN + BOLD,
                CYAN + BOLD,
                WHITE,
                RESET
        );
    }

    /**
     * Reads multiline analyzer input with double-enter completion.
     *
     * @param typeName Selected content type name.
     * @return Trimmed multiline content text.
     */
    private String readContentInput(String typeName) {
        TerminalUtils.printCenteredLine(
                "Paste your " + typeName + " below. Press Enter twice when done:",
                WHITE,
                RESET
        );
        return readMultilineTextUntilDoubleEnter();
    }

    /**
     * Executes selected analyzer action and validates action choice.
     *
     * @param analyzer Selected analyzer implementation.
     * @param analysisChoice Analyzer menu choice.
     * @param contentText User content text.
     * @return Analysis result text or null when choice is invalid.
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
            return GEMINI_FALLBACK_RESPONSE;
        }
    }

    /**
     * Asks yes/no with validation.
     *
     * @param question Prompt question.
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
                    "Valid answers: yes, y, no, n."
            );
        }
    }

    /**
     * Reads one line via styled prompt.
     *
     * @param prompt Prompt label text.
     * @return Trimmed user input.
     */
    private String readLine(String prompt) {
        return TerminalUtils.prompt(scanner, prompt, CYAN, WHITE, RESET);
    }

    /**
     * Reads multiline content until user presses Enter twice consecutively.
     *
     * @param none This method does not receive arguments.
     * @return Trimmed multiline input.
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
     * Prints summary box and required post-summary stats, keywords, and divider.
     *
     * @param originalText Original content text.
     * @param summaryText Final summary text.
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

        String topKeywords = resolveTopKeywords(summaryText);
        TerminalUtils.printTopKeywords(topKeywords, YELLOW, BOLD, RESET);

        TerminalUtils.printGreenDivider(GREEN, RESET);
    }

    /**
     * Resolves top 5 keywords from summary using Gemini with local fallback.
     *
     * @param summaryText Summary text source.
     * @return Comma-separated keyword list.
     */
    private String resolveTopKeywords(String summaryText) {
        String keywords = geminiService.extractTopKeywords(summaryText);
        if (isGeminiFallbackResponse(keywords) || keywords.isBlank()) {
            return buildLocalKeywords(summaryText);
        }
        return keywords;
    }

    /**
     * Checks if text equals the standard Gemini fallback response.
     *
     * @param value Response value.
     * @return True when fallback text is detected.
     */
    private boolean isGeminiFallbackResponse(String value) {
        if (value == null) {
            return true;
        }
        return GEMINI_FALLBACK_RESPONSE.equalsIgnoreCase(value.trim());
    }

    /**
     * Builds local keyword fallback when Gemini keyword extraction is unavailable.
     *
     * @param text Source text.
     * @return Five comma-separated fallback keywords.
     */
    private String buildLocalKeywords(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return "analysis, content, summary, themes, insights";
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
            return "analysis, content, summary, themes, insights";
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((left, right) -> {
            int compareCount = Integer.compare(right.getValue(), left.getValue());
            if (compareCount != 0) {
                return compareCount;
            }
            return left.getKey().compareTo(right.getKey());
        });

        List<String> keywords = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : entries) {
            keywords.add(entry.getKey());
            if (keywords.size() == 5) {
                break;
            }
        }

        while (keywords.size() < 5) {
            keywords.add("analysis");
        }

        return String.join(", ", keywords);
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
     * Normalizes empty results into one safe fallback message.
     *
     * @param result Raw analyzer result.
     * @return Safe non-empty result text.
     */
    private String safeResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            return GEMINI_FALLBACK_RESPONSE;
        }
        return result.trim();
    }
}
