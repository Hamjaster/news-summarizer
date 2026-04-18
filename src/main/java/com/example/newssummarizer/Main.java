/*
 * This file starts the app from the terminal.
 * It reads your search query and keeps the app running in a loop.
 * It sends your text to Gemini for cleanup and then to the summarizer API.
 * It prints the final summary or a clear error message.
 */
package com.example.newssummarizer;

import java.io.IOException;
import java.util.Scanner;

public class Main {

    /**
     * Runs the terminal application loop and coordinates all program steps.
     *
     * @param args Command-line arguments provided at launch (not required by this app).
     * @return Nothing.
     */
    public static void main(String[] args) {
        GeminiService geminiService = new GeminiService();
        SummarizerService summarizerService = new SummarizerService();

        try (Scanner scanner = new Scanner(System.in)) {
            boolean continueRunning = true;

            while (continueRunning) {
                String query = readQuery(scanner);
                if (query.isBlank()) {
                    System.out.println("Error: Query cannot be empty. Please enter some text.");
                    continue;
                }

                try {
                    String formattedQuery = geminiService.formatQueryWithGemini(query);
                    String summary = summarizerService.getSummary(formattedQuery);

                    System.out.println("\nFormatted Query: " + formattedQuery);
                    System.out.println("\nSummary:\n" + summary + "\n");
                } catch (IOException exception) {
                    printError(exception.getMessage());
                } catch (Exception exception) {
                    printError("Error: Something unexpected happened. Please try again.");
                }

                continueRunning = askToContinue(scanner);
            }
        } catch (Exception exception) {
            printError("Error: The application stopped unexpectedly. Please restart and try again.");
        }
    }

    /**
     * Prompts the user for a search query and safely reads one full line.
     *
     * @param scanner Shared Scanner instance used to read terminal input.
     * @return The trimmed user query, or an empty string when input is missing.
     */
    private static String readQuery(Scanner scanner) {
        System.out.print("Enter your search query: ");
        String query = scanner.nextLine();
        if (query == null) {
            return "";
        }
        return query.trim();
    }

    /**
     * Asks the user whether to run another search cycle.
     *
     * @param scanner Shared Scanner instance used to read terminal input.
     * @return True when the user wants another run, otherwise false.
     */
    private static boolean askToContinue(Scanner scanner) {
        System.out.print("Do you want to search again? (y/n): ");
        String answer = scanner.nextLine();
        if (answer == null) {
            return false;
        }
        return answer.trim().equalsIgnoreCase("y");
    }

    /**
     * Prints a friendly error message for users in one consistent format.
     *
     * @param message Message text to show to the user.
     * @return Nothing.
     */
    private static void printError(String message) {
        if (message == null || message.isBlank()) {
            System.out.println("Error: An unknown problem occurred.");
            return;
        }
        System.out.println(message);
    }
}