/*
 * This is where the app starts.
 * It shows the main menu to the user and sends them to the right feature based on their choice.
 * Think of it as the front door of the application.
 * It keeps startup simple so everything begins in one place.
 */
package com.example.newssummarizer;

public class Main {

    /**
     * Starts the application by handing control to the menu screen.
     *
     * @param args Command-line arguments passed from the terminal.
     * @return Nothing. The method exits when the user chooses to quit.
     */
    public static void main(String[] args) {
        try {
            MenuUI menuUI = new MenuUI();
            menuUI.start();
        } catch (Exception exception) {
            printStartupError();
        }
    }

    /**
     * Prints a bordered startup error when the app cannot begin normally.
     *
     * @param none This method does not receive arguments.
     * @return Nothing. It only prints to the terminal.
     */
    private static void printStartupError() {
        System.out.println("\u2554" + "\u2550".repeat(54) + "\u2557");
        System.out.println("\u2551  ERROR: The application could not start correctly.    \u2551");
        System.out.println("\u2551  Please try running it again.                         \u2551");
        System.out.println("\u255A" + "\u2550".repeat(54) + "\u255D");
    }
}