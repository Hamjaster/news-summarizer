/*
 * This file contains helper tools for making the terminal look good.
 * It handles colors, loading animations, text wrapping, and box drawing
 * so every other file stays clean and focused
 * on logic.
 */
package com.example.newssummarizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public final class TerminalUtils {

    public static final int BOX_WIDTH = 80;
    public static final int INNER_WIDTH = 76;
    public static final int SUMMARY_WRAP_WIDTH = 72;
    public static final String INDENT = "    ";

    private TerminalUtils() {
        // Utility class.
    }

    /**
     * Pauses the current thread for a controlled terminal timing effect.
     *
     * @param milliseconds Pause duration in milliseconds.
     * @return Nothing. This method sleeps the current thread.
     */
    public static void pause(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Shows a simple dot animation to indicate work is in progress.
     *
     * @param message Base loading text shown before animated dots.
     * @return Nothing. This method prints loading frames to terminal.
     */
    public static void showLoading(String message) {
        String yellow = "\u001B[33m";
        String reset = "\u001B[0m";
        String baseMessage = (message == null || message.isBlank()) ? "Please wait" : message.trim();

        System.out.println();
        for (int cycle = 0; cycle < 2; cycle++) {
            for (int dots = 1; dots <= 3; dots++) {
                String frame = baseMessage + " " + ".".repeat(dots);
                String centeredFrame = centerText(frame, BOX_WIDTH);
                System.out.println(INDENT + yellow + centeredFrame + reset);
                pause(400);
            }
        }
        System.out.println();
    }

    /**
     * Prints a centered one-line message with optional style and spacing.
     *
     * @param text Message text.
     * @param style ANSI style prefix such as color or bold combinations.
     * @param reset ANSI reset code.
     * @return Nothing. It prints directly to terminal.
     */
    public static void printCenteredLine(String text, String style, String reset) {
        String safeText = text == null ? "" : text;
        String safeStyle = style == null ? "" : style;
        String safeReset = reset == null ? "" : reset;
        System.out.println(INDENT + safeStyle + centerText(safeText, BOX_WIDTH) + safeReset);
    }

    /**
     * Prints a centered 80-character titled box with wrapped content lines.
     *
     * @param title Title shown in the first row of the box.
     * @param contentLines Content rows to wrap and print.
     * @param borderStyle ANSI style for borders.
     * @param titleStyle ANSI style for title text.
     * @param contentStyle ANSI style for content text.
     * @param reset ANSI reset code.
     * @return Nothing. It prints directly to terminal.
     */
    public static void printTitledBox(
            String title,
            List<String> contentLines,
            String borderStyle,
            String titleStyle,
            String contentStyle,
            String reset
    ) {
        String safeBorderStyle = borderStyle == null ? "" : borderStyle;
        String safeTitleStyle = titleStyle == null ? "" : titleStyle;
        String safeContentStyle = contentStyle == null ? "" : contentStyle;
        String safeReset = reset == null ? "" : reset;

        System.out.println();
        printRawLine(safeBorderStyle + "╔" + "═".repeat(BOX_WIDTH - 2) + "╗" + safeReset);
        printRawLine(safeBorderStyle + "║ " + safeTitleStyle + centerText(title == null ? "" : title, INNER_WIDTH)
                + safeBorderStyle + " ║" + safeReset);
        printRawLine(safeBorderStyle + "╠" + "═".repeat(BOX_WIDTH - 2) + "╣" + safeReset);

        List<String> expandedLines = new ArrayList<>();
        if (contentLines != null) {
            for (String line : contentLines) {
                expandedLines.addAll(wrapText(line == null ? "" : line, INNER_WIDTH));
            }
        }

        if (expandedLines.isEmpty()) {
            expandedLines.add("");
        }

        for (String line : expandedLines) {
            printRawLine(safeBorderStyle + "║ " + safeContentStyle + padRight(line, INNER_WIDTH)
                    + safeBorderStyle + " ║" + safeReset);
        }

        printRawLine(safeBorderStyle + "╚" + "═".repeat(BOX_WIDTH - 2) + "╝" + safeReset);
        System.out.println();
    }

    /**
     * Prints a centered 80-character box without a title row.
     *
     * @param contentLines Content rows to wrap and print.
     * @param borderStyle ANSI style for borders.
     * @param contentStyle ANSI style for content text.
     * @param reset ANSI reset code.
     * @return Nothing. It prints directly to terminal.
     */
    public static void printSimpleBox(List<String> contentLines, String borderStyle, String contentStyle, String reset) {
        String safeBorderStyle = borderStyle == null ? "" : borderStyle;
        String safeContentStyle = contentStyle == null ? "" : contentStyle;
        String safeReset = reset == null ? "" : reset;

        System.out.println();
        printRawLine(safeBorderStyle + "╔" + "═".repeat(BOX_WIDTH - 2) + "╗" + safeReset);

        List<String> expandedLines = new ArrayList<>();
        if (contentLines != null) {
            for (String line : contentLines) {
                expandedLines.addAll(wrapText(line == null ? "" : line, INNER_WIDTH));
            }
        }

        if (expandedLines.isEmpty()) {
            expandedLines.add("");
        }

        for (String line : expandedLines) {
            printRawLine(safeBorderStyle + "║ " + safeContentStyle + padRight(line, INNER_WIDTH)
                    + safeBorderStyle + " ║" + safeReset);
        }

        printRawLine(safeBorderStyle + "╚" + "═".repeat(BOX_WIDTH - 2) + "╝" + safeReset);
        System.out.println();
    }

    /**
     * Prints the standard summary box with 72-char justified text inside an 80-char frame.
     *
     * @param title Summary title text.
     * @param summary Summary content text.
     * @param borderStyle ANSI style for borders.
     * @param titleStyle ANSI style for title text.
     * @param contentStyle ANSI style for summary content.
     * @param reset ANSI reset code.
     * @return Nothing. It prints directly to terminal.
     */
    public static void printSummaryBox(
            String title,
            String summary,
            String borderStyle,
            String titleStyle,
            String contentStyle,
            String reset
    ) {
        String safeSummary = (summary == null || summary.trim().isEmpty())
                ? "Could not complete request. Please try again."
                : summary.trim();

        List<String> justifiedLines = wrapAndJustify(safeSummary, SUMMARY_WRAP_WIDTH);
        List<String> boxLines = new ArrayList<>();
        for (String justifiedLine : justifiedLines) {
            boxLines.add("  " + padRight(justifiedLine, SUMMARY_WRAP_WIDTH) + "  ");
        }

        printTitledBox(title, boxLines, borderStyle, titleStyle, contentStyle, reset);
    }

    /**
     * Prints a centered styled prompt and returns one trimmed input line.
     *
     * @param scanner Scanner used to read terminal input.
     * @param promptText Prompt text shown after the arrow.
     * @param cyan ANSI cyan code for the arrow.
     * @param white ANSI white code for the prompt text.
     * @param reset ANSI reset code.
     * @return Trimmed user input.
     */
    public static String prompt(Scanner scanner, String promptText, String cyan, String white, String reset) {
        String safePrompt = promptText == null ? "" : promptText;
        String plainPrompt = "→ " + safePrompt;
        String centeredPrompt = centerText(plainPrompt, BOX_WIDTH);

        System.out.println();
        int arrowIndex = centeredPrompt.indexOf("→");
        if (arrowIndex >= 0) {
            String beforeArrow = centeredPrompt.substring(0, arrowIndex);
            String afterArrow = centeredPrompt.substring(arrowIndex + 1);
            System.out.println(INDENT + beforeArrow + cyan + "→" + reset + white + afterArrow + reset);
        } else {
            System.out.println(INDENT + white + centeredPrompt + reset);
        }
        System.out.println();

        String input = scanner.nextLine();
        return input == null ? "" : input.trim();
    }

    /**
     * Prints one yellow compression stats line below a summary.
     *
     * @param originalText Source text before summarization.
     * @param summaryText Summary text after summarization.
     * @param yellow ANSI yellow code.
     * @param reset ANSI reset code.
     * @return Nothing. It prints directly to terminal.
     */
    public static void printWordStats(String originalText, String summaryText, String yellow, String reset) {
        int originalCount = countWords(originalText);
        int summaryCount = countWords(summaryText);
        int compression = 0;
        if (originalCount > 0) {
            compression = (int) Math.round((1.0 - ((double) summaryCount / (double) originalCount)) * 100.0);
        }
        if (compression < 0) {
            compression = 0;
        }

        String line = "Original: " + originalCount + " words   →   Summary: " + summaryCount
                + " words   →   Compressed: " + compression + "%";

        System.out.println();
        printCenteredLine(line, yellow, reset);
        System.out.println();
    }

    /**
     * Prints highlighted top keywords in yellow and bold style.
     *
     * @param commaSeparatedKeywords Keywords separated by commas.
     * @param yellow ANSI yellow code.
     * @param bold ANSI bold code.
     * @param reset ANSI reset code.
     * @return Nothing. It prints directly to terminal.
     */
    public static void printTopKeywords(String commaSeparatedKeywords, String yellow, String bold, String reset) {
        String safeKeywords = commaSeparatedKeywords == null ? "" : commaSeparatedKeywords.trim();
        String displayKeywords = safeKeywords;

        if (displayKeywords.isEmpty()) {
            displayKeywords = "n/a";
        } else {
            String[] pieces = displayKeywords.split(",");
            List<String> trimmedPieces = new ArrayList<>();
            for (String piece : pieces) {
                String trimmed = piece.trim();
                if (!trimmed.isEmpty()) {
                    trimmedPieces.add(trimmed);
                }
            }
            if (trimmedPieces.isEmpty()) {
                displayKeywords = "n/a";
            } else {
                displayKeywords = String.join("  |  ", trimmedPieces);
            }
        }

        String line = "Top Keywords:  " + displayKeywords;
        System.out.println();
        printCenteredLine(line, yellow + bold, reset);
        System.out.println();
    }

    /**
     * Prints a green divider line with spacing before and after.
     *
     * @param green ANSI green code.
     * @param reset ANSI reset code.
     * @return Nothing. It prints directly to terminal.
     */
    public static void printGreenDivider(String green, String reset) {
        System.out.println();
        System.out.println(INDENT + green + "─".repeat(BOX_WIDTH) + reset);
        System.out.println();
    }

    /**
     * Wraps text to the requested width preserving word boundaries when possible.
     *
     * @param text Source text.
     * @param width Target wrap width.
     * @return Wrapped lines.
     */
    public static List<String> wrapText(String text, int width) {
        List<String> lines = new ArrayList<>();
        String safeText = text == null ? "" : text;

        String normalized = safeText
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.isEmpty()) {
            lines.add("");
            return lines;
        }

        String remaining = normalized;
        while (remaining.length() > width) {
            int splitIndex = remaining.lastIndexOf(' ', width);
            if (splitIndex <= 0) {
                splitIndex = width;
            }

            lines.add(remaining.substring(0, splitIndex).trim());
            remaining = remaining.substring(splitIndex).trim();
        }

        if (!remaining.isEmpty()) {
            lines.add(remaining);
        }

        return lines;
    }

    /**
     * Wraps and justifies text to produce balanced summary lines.
     *
     * @param text Source text.
     * @param width Target line width.
     * @return Wrapped and justified lines.
     */
    public static List<String> wrapAndJustify(String text, int width) {
        List<String> wrappedLines = wrapText(text, width);
        List<String> justifiedLines = new ArrayList<>();

        for (int index = 0; index < wrappedLines.size(); index++) {
            String line = wrappedLines.get(index);
            if (index == wrappedLines.size() - 1) {
                justifiedLines.add(line);
            } else {
                justifiedLines.add(justifyLine(line, width));
            }
        }

        return justifiedLines;
    }

    /**
     * Centers plain text into a fixed width field.
     *
     * @param text Text to center.
     * @param width Target width.
     * @return Centered text.
     */
    public static String centerText(String text, int width) {
        String safeText = text == null ? "" : text;
        if (safeText.length() >= width) {
            return safeText;
        }

        int totalPadding = width - safeText.length();
        int leftPadding = totalPadding / 2;
        int rightPadding = totalPadding - leftPadding;
        return " ".repeat(leftPadding) + safeText + " ".repeat(rightPadding);
    }

    /**
     * Counts words using whitespace splitting.
     *
     * @param text Source text.
     * @return Number of words.
     */
    public static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }

        String[] words = text.trim().split("\\s+");
        int count = 0;
        for (String word : words) {
            if (!word.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Prints one already formatted line with shared indentation.
     *
     * @param value Preformatted line text.
     * @return Nothing. It prints directly to terminal.
     */
    private static void printRawLine(String value) {
        System.out.println(INDENT + value);
    }

    /**
     * Pads text with trailing spaces to a fixed width.
     *
     * @param text Source text.
     * @param width Target width.
     * @return Right-padded text.
     */
    private static String padRight(String text, int width) {
        String safeText = text == null ? "" : text;
        if (safeText.length() >= width) {
            return safeText;
        }
        return safeText + " ".repeat(width - safeText.length());
    }

    /**
     * Justifies one line by distributing extra spaces across words.
     *
     * @param line Source line.
     * @param width Target width.
     * @return Justified line text.
     */
    private static String justifyLine(String line, int width) {
        String safeLine = line == null ? "" : line.trim();
        if (safeLine.isEmpty()) {
            return "";
        }

        String[] words = safeLine.split("\\s+");
        if (words.length == 1) {
            return padRight(safeLine, width);
        }

        int wordsLength = 0;
        for (String word : words) {
            wordsLength += word.length();
        }

        int gaps = words.length - 1;
        int totalSpaces = width - wordsLength;
        int baseSpaces = totalSpaces / gaps;
        int extraSpaces = totalSpaces % gaps;

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < words.length; index++) {
            builder.append(words[index]);
            if (index < gaps) {
                int spaces = baseSpaces + (index < extraSpaces ? 1 : 0);
                builder.append(" ".repeat(Math.max(1, spaces)));
            }
        }

        return builder.toString();
    }
}