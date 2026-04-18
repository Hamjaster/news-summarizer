/*
 * This is where the app starts.
 * It shows the main menu to the user and sends them to the right feature based on their choice.
 * Think of it as the front door of the application.
 * It keeps startup simple so everything begins in one place.
 */
package com.example.newssummarizer;

import java.util.ArrayList;
import java.util.List;

public class Main {//The purpose here basically is to start the program itself

    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";

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
        List<String> lines = new ArrayList<>();
        lines.add("ERROR: The application could not start correctly.");
        lines.add("Please try running it again.");
        TerminalUtils.printSimpleBox(lines, RED + BOLD, RED, RESET);
    }
}