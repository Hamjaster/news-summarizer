/*
 * This file talks to Google Gemini AI.
 * It does three things: turns a user question into good search keywords.
 * It summarizes news articles and also summarizes pasted text.
 * It keeps all Gemini communication in one place.
 */
package com.example.newssummarizer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class GeminiService {

    private static final String GEMINI_API_KEY_ENV = "GEMINI_API_KEY";
    private static final String GEMINI_ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=";
    private static final String FALLBACK_RESPONSE = "Could not complete request. Please try again.";
    private static final int[] RATE_LIMIT_RETRY_DELAYS_SECONDS = {15, 30, 60};

    private final HttpClient httpClient;
    private final String geminiApiKey;
    private volatile String lastFailureReason;

    /**
     * Creates a reusable Gemini service with one shared HTTP client.
     *
     * @param none This constructor does not take inputs.
     * @return A ready GeminiService object.
     */
    public GeminiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        this.lastFailureReason = "";

        String configuredGeminiKey = System.getenv(GEMINI_API_KEY_ENV);
        if (configuredGeminiKey == null || configuredGeminiKey.trim().isEmpty()) {
            setLastFailureReason("GEMINI_API_KEY is missing.");
            printErrorBox(
                    "ERROR: GEMINI_API_KEY is missing.",
                    "Please check your .env file and set GEMINI_API_KEY.",
                    "The application will now exit gracefully."
            );
            this.geminiApiKey = "";
            System.exit(1);
            return;
        }

        this.geminiApiKey = configuredGeminiKey.trim();
    }

    /**
     * Returns the most recent Gemini failure reason in user-readable text.
     *
     * @param none This method does not receive arguments.
     * @return A concise failure reason, or an empty string when no recent failure exists.
     */
    public String getLastFailureReason() {
        if (lastFailureReason == null) {
            return "";
        }
        return lastFailureReason.trim();
    }

    /**
     * Takes the user's natural language question and asks Gemini to extract clean search keywords optimized for NewsAPI.
     *
     * @param userQuestion The full question typed by the user.
     * @return A short keyword string such as "Trump Iran statement".
     */
    public String formatQueryForNews(String userQuestion) {
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: Your question is empty.",
                    "Please enter a real question before searching."
            );
            return FALLBACK_RESPONSE;
        }

        String prompt = "Extract the most important search keywords from this question for a news API query. "
                + "Return only the keywords, no explanation, no punctuation, just space-separated words. Question: "
                + userQuestion.trim();

        String geminiResponse = sendPrompt(prompt);
        if (FALLBACK_RESPONSE.equals(geminiResponse)) {
            return FALLBACK_RESPONSE;
        }

        String cleanedKeywords = geminiResponse
                .replaceAll("[\\p{Punct}]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleanedKeywords.isEmpty()) {
            printErrorBox(
                    "ERROR: Gemini returned empty keywords.",
                    "Please try your question again."
            );
            return FALLBACK_RESPONSE;
        }

        return cleanedKeywords;
    }

    /**
     * Takes all article titles and descriptions combined into one text, sends to Gemini, and returns one clear summary of all events.
     *
     * @param combinedArticles All article text combined together.
     * @return A single paragraph summary of the main news events.
     */
    public String summarizeArticles(String combinedArticles) {
        if (combinedArticles == null || combinedArticles.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: No article content is available to summarize.",
                    "Please search again with another question."
            );
            return FALLBACK_RESPONSE;
        }

        String prompt = "You are a news summarizer. Below are multiple news article titles and descriptions. "
                + "Read them all and write one clear, concise summary of the main events. "
                + "Use simple language. Do not use bullet points. Write in paragraph form.\n\n"
                + "Articles:\n"
                + combinedArticles;

        return sendPrompt(prompt);
    }

    /**
     * Takes any text the user pastes and returns a clear summary that is roughly 25 to 30 percent of the original length.
     *
     * @param userText The full text that the user pasted in the terminal.
     * @return A concise summary that keeps the main meaning.
     */
    public String summarizeText(String userText) {
        if (userText == null || userText.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: Your text input is empty.",
                    "Please paste text and type END on a new line."
            );
            return FALLBACK_RESPONSE;
        }

        String prompt = "Summarize the following content clearly and concisely. "
                + "Keep roughly 25 to 30 percent of the original length. "
                + "Make sure the summary is coherent and reads naturally.\n\n"
                + "Text:\n"
                + userText;

        return sendPrompt(prompt);
    }

    /**
     * Sends a prompt to Gemini and extracts candidates[0].content.parts[0].text from the response.
     *
     * @param prompt The instruction text that should be sent to Gemini.
     * @return Gemini text output, or a fallback message on failure.
     */
    private String sendPrompt(String prompt) {
        try {
            setLastFailureReason("");

            JSONObject payload = new JSONObject();
            JSONArray contentsArray = new JSONArray();
            JSONObject contentObject = new JSONObject();
            JSONArray partsArray = new JSONArray();

            partsArray.put(new JSONObject().put("text", prompt));
            contentObject.put("parts", partsArray);
            contentsArray.put(contentObject);
            payload.put("contents", contentsArray);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_ENDPOINT_BASE + geminiApiKey))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = null;
            for (int attempt = 0; attempt <= RATE_LIMIT_RETRY_DELAYS_SECONDS.length; attempt++) {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() == 429 && attempt < RATE_LIMIT_RETRY_DELAYS_SECONDS.length) {
                    int waitSeconds = RATE_LIMIT_RETRY_DELAYS_SECONDS[attempt];
                    printRateLimitRetryBox(waitSeconds);
                    Thread.sleep(waitSeconds * 1000L);
                    continue;
                }

                break;
            }

            if (response == null) {
                setLastFailureReason("Gemini request failed before receiving a response.");
                printErrorBox(
                        "ERROR: Gemini request failed before receiving a response.",
                        "Please try again in a moment."
                );
                return FALLBACK_RESPONSE;
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                handleGeminiHttpError(response.statusCode(), response.body());
                return FALLBACK_RESPONSE;
            }

            String responseBody = response.body();
            if (responseBody == null || responseBody.trim().isEmpty()) {
                setLastFailureReason("Gemini returned an empty response.");
                printErrorBox(
                        "ERROR: Gemini returned an empty response.",
                        "Please try again in a moment."
                );
                return FALLBACK_RESPONSE;
            }

            JSONObject root = new JSONObject(responseBody);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                setLastFailureReason("Gemini returned no candidates.");
                printErrorBox(
                        "ERROR: Gemini did not return any candidates.",
                        "Please try the request again."
                );
                return FALLBACK_RESPONSE;
            }

            JSONObject firstCandidate = candidates.optJSONObject(0);
            if (firstCandidate == null) {
                setLastFailureReason("Gemini candidate format is invalid.");
                printErrorBox(
                        "ERROR: Gemini candidate format is invalid.",
                        "Please try again."
                );
                return FALLBACK_RESPONSE;
            }

            JSONObject content = firstCandidate.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            JSONObject firstPart = parts == null ? null : parts.optJSONObject(0);
            String text = firstPart == null ? "" : firstPart.optString("text", "").trim();

            if (text.isEmpty()) {
                setLastFailureReason("Gemini response text is empty.");
                printErrorBox(
                        "ERROR: Gemini response text is empty.",
                        "Please try again."
                );
                return FALLBACK_RESPONSE;
            }

            return text;
        } catch (HttpTimeoutException exception) {
            setLastFailureReason("Gemini request timed out.");
            printErrorBox(
                    "ERROR: Gemini request timed out.",
                    "Please check your internet and try again."
            );
            return FALLBACK_RESPONSE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            setLastFailureReason("Gemini request was interrupted.");
            printErrorBox(
                    "ERROR: Gemini request was interrupted.",
                    "Please try again."
            );
            return FALLBACK_RESPONSE;
        } catch (IOException exception) {
            setLastFailureReason("Could not reach Gemini service.");
            printErrorBox(
                    "ERROR: Could not reach Gemini.",
                    "Please check your connection and try again."
            );
            return FALLBACK_RESPONSE;
        } catch (Exception exception) {
            setLastFailureReason("Gemini returned malformed data.");
            printErrorBox(
                    "ERROR: Gemini returned malformed data.",
                    "Please try again with a different input."
            );
            return FALLBACK_RESPONSE;
        }
    }

    /**
     * Prints a clear Gemini HTTP error message based on status code and response details.
     *
     * @param statusCode The HTTP status code returned by Gemini.
     * @param responseBody The raw response body from Gemini.
     * @return Nothing. This method only prints messages.
     */
    private void handleGeminiHttpError(int statusCode, String responseBody) {
        String apiMessage = compactMessage(extractApiErrorMessage(responseBody), 180);

        if (statusCode == 429) {
            setLastFailureReason(apiMessage.isEmpty()
                    ? "Gemini quota or rate limit reached (HTTP 429)."
                    : "Gemini quota or rate limit reached (HTTP 429): " + apiMessage);
            printErrorBox(
                    "ERROR: Gemini rate limit or quota reached (HTTP 429).",
                    "Please wait a bit or use a different Gemini API key.",
                    apiMessage.isEmpty() ? "" : "Details: " + apiMessage
            );
            return;
        }

        if (statusCode == 401 || statusCode == 403) {
            setLastFailureReason(apiMessage.isEmpty()
                ? "Gemini API key is invalid or blocked (HTTP " + statusCode + ")."
                : "Gemini API key is invalid or blocked (HTTP " + statusCode + "): " + apiMessage);
            printErrorBox(
                    "ERROR: Gemini API key is invalid or blocked.",
                    "Please update the key and try again.",
                    apiMessage.isEmpty() ? "" : "Details: " + apiMessage
            );
            return;
        }

            setLastFailureReason(apiMessage.isEmpty()
                ? "Gemini request failed with status " + statusCode + "."
                : "Gemini request failed with status " + statusCode + ": " + apiMessage);

        printErrorBox(
                "ERROR: Gemini request failed with status " + statusCode + ".",
                apiMessage.isEmpty() ? "Please try again later." : "Details: " + apiMessage
        );
    }

    /**
     * Extracts a readable error message from Gemini JSON error responses.
     *
     * @param responseBody Raw Gemini response body.
     * @return Error message text when available, otherwise an empty string.
     */
    private String extractApiErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return "";
        }

        try {
            JSONObject root = new JSONObject(responseBody);
            JSONObject errorObject = root.optJSONObject("error");
            if (errorObject == null) {
                return "";
            }

            return errorObject.optString("message", "").trim();
        } catch (Exception exception) {
            return "";
        }
    }

    /**
     * Cleans and shortens long API error details for readable terminal output.
     *
     * @param value The original error message text.
     * @param maxLength Maximum characters to keep.
     * @return A compact single-line message.
     */
    private String compactMessage(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String compact = value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (compact.length() <= maxLength) {
            return compact;
        }

        return compact.substring(0, maxLength) + "...";
    }

    /**
     * Prints the requested bordered wait message before retrying on HTTP 429.
     *
     * @param waitSeconds Seconds to wait before the next retry attempt.
     * @return Nothing. This method prints directly to the terminal.
     */
    private void printRateLimitRetryBox(int waitSeconds) {
        int contentWidth = 42;
        String message = "  Rate limit hit. Retrying in " + waitSeconds + "s...";

        System.out.println("\u2554" + "\u2550".repeat(contentWidth) + "\u2557");
        System.out.println("\u2551" + padRight(message, contentWidth) + "\u2551");
        System.out.println("\u255A" + "\u2550".repeat(contentWidth) + "\u255D");
    }

    /**
     * Stores the most recent Gemini failure reason used by UI fallbacks.
     *
     * @param reason Failure reason text.
     * @return Nothing. This method updates in-memory state only.
     */
    private void setLastFailureReason(String reason) {
        this.lastFailureReason = reason == null ? "" : reason.trim();
    }

    /**
     * Prints a clear bordered error message to the terminal.
     *
     * @param messageLines The error message lines to show in the box.
     * @return Nothing. This method only prints to the terminal.
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
     * Pads text with spaces so all boxed lines align.
     *
     * @param value The original text value.
     * @param width The target width.
     * @return The padded text.
     */
    private String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }
}