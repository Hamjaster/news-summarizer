/*
 * This file talks to Google Gemini AI.
 * It formats keyword queries, summarizes content, and provides helper prompts.
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
import java.util.ArrayList;
import java.util.List;

public class GeminiService {

    private static final String GEMINI_API_KEY_ENV = "GEMINI_API_KEY";
    private static final String GEMINI_ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=";
    private static final String FALLBACK_RESPONSE = "Could not complete request. Please try again.";
    private static final int[] RATE_LIMIT_RETRY_DELAYS_SECONDS = {15, 30, 60};
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BOLD = "\u001B[1m";
    private static final String RESET = "\u001B[0m";

    private final HttpClient httpClient;
    private final String geminiApiKey;
    private volatile String lastFailureReason;

    /**
     * Creates a reusable Gemini service with one shared HTTP client.
     *
     * @param none This constructor does not receive arguments.
     * @return A ready GeminiService object.
     */
    public GeminiService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        this.lastFailureReason = "";

        // Read from .env overrides first to avoid reflective env hacks on Java 25.
        String configuredGeminiKey = DotEnvLoader.getEnv(GEMINI_API_KEY_ENV);
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
     * Returns the most recent Gemini failure reason in readable text.
     *
     * @param none This method does not receive arguments.
     * @return Last failure reason text.
     */
    public String getLastFailureReason() {
        if (lastFailureReason == null) {
            return "";
        }
        return cleanResponse(lastFailureReason.trim());
    }

    /**
     * Extracts query keywords from a natural-language user question.
     *
     * @param userQuestion Full user question text.
     * @return Keyword text for NewsAPI search.
     */
    public String formatQueryForNews(String userQuestion) {
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: Your question is empty.",
                    "Please enter a real question before searching."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        }

        String prompt = "Extract the most important search keywords from this question for a news API query. "
                + "Return only the keywords, no explanation, no punctuation, just space-separated words. Question: "
                + userQuestion.trim();

        String geminiResponse = sendPrompt(prompt);
        if (FALLBACK_RESPONSE.equals(geminiResponse)) {
            return cleanResponse(FALLBACK_RESPONSE);
        }

        String cleanedKeywords = cleanResponse(geminiResponse)
                .replaceAll("[\\p{Punct}]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleanedKeywords.isEmpty()) {
            printErrorBox(
                    "ERROR: Gemini returned empty keywords.",
                    "Please try your question again."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        }

        return cleanResponse(cleanedKeywords);
    }

    /**
     * Summarizes multiple news articles into one coherent response.
     *
     * @param combinedArticles Combined article text.
     * @return Summary text.
     */
    public String summarizeArticles(String combinedArticles) {
        if (combinedArticles == null || combinedArticles.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: No article content is available to summarize.",
                    "Please search again with another question."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        }

        String prompt = "You are a senior news editor writing a reader-friendly briefing. "
                + "Below are multiple news article titles and descriptions on a related topic. "
                + "Read them all and write a thorough, engaging briefing of FIVE to SEVEN well-developed paragraphs "
                + "(at least 350 words total). Structure it as: "
                + "(1) an opening paragraph that frames the story and why it matters now; "
                + "(2) two or three paragraphs unpacking the key facts, players, numbers, and direct details from the articles; "
                + "(3) a paragraph on context, background, or broader implications; "
                + "(4) a closing paragraph on what to watch for next. "
                + "Use clear, vivid language. Write in flowing paragraphs only — no bullet points, no headings, no markdown. "
                + "Do not invent facts; rely only on what the articles say.\n\n"
                + "Articles:\n"
                + combinedArticles;

        return cleanResponse(sendPrompt(prompt));
    }

    /**
     * Summarizes article text using a caller-provided instruction prompt.
     *
     * @param combinedArticles Combined article text.
     * @param summaryInstruction Custom instruction prefix for Gemini.
     * @return Summary text.
     */
    public String summarizeArticles(String combinedArticles, String summaryInstruction) {
        if (combinedArticles == null || combinedArticles.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: No article content is available to summarize.",
                    "Please search again with another question."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        }

        if (summaryInstruction == null || summaryInstruction.trim().isEmpty()) {
            return summarizeArticles(combinedArticles);
        }

        String prompt = summaryInstruction.trim() + "\n\nHeadlines:\n" + combinedArticles;
        return cleanResponse(sendPrompt(prompt));
    }

    /**
     * Returns a length instruction phrase for the given preset.
     */
    private String lengthInstruction(String preset) {
        if (preset == null) preset = "";
        switch (preset.trim().toLowerCase()) {
            case "short":
                return "Keep it short: write ONE to TWO paragraphs, roughly 90 to 130 words total.";
            case "long":
                return "Make it thorough: write FIVE to SEVEN well-developed paragraphs, at least 450 words total.";
            case "medium":
            default:
                return "Make it medium-length: write THREE to FOUR paragraphs, roughly 230 to 300 words total.";
        }
    }

    /**
     * Summarizes user text at a chosen length preset (Short/Medium/Long).
     *
     * @param userText Raw user text.
     * @param lengthPreset Length preset name.
     * @return Summarized text.
     */
    public String summarizeText(String userText, String lengthPreset) {
        if (userText == null || userText.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: Your text input is empty.",
                    "Paste your text and press Summarize."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        }

        String prompt = "Summarize the following content clearly and accurately. "
                + lengthInstruction(lengthPreset) + " "
                + "Write in flowing, well-developed paragraphs. "
                + "Preserve key names, numbers, quotes, and cause-and-effect. "
                + "No bullet points, no headings, no markdown.\n\n"
                + "Text:\n"
                + userText;

        return cleanResponse(sendPrompt(prompt));
    }

    /**
     * Summarizes news articles into a briefing at a chosen length preset.
     *
     * @param combinedArticles Combined article text.
     * @param lengthPreset Length preset name.
     * @return Briefing text.
     */
    public String summarizeArticlesWithLength(String combinedArticles, String lengthPreset) {
        if (combinedArticles == null || combinedArticles.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: No article content is available to summarize.",
                    "Please search again with another question."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        }

        String prompt = "You are a senior news editor writing a reader-friendly briefing. "
                + "Below are multiple news article titles and descriptions on a related topic. "
                + lengthInstruction(lengthPreset) + " "
                + "Cover the key facts, players, numbers, context, and what to watch next. "
                + "Use clear, vivid language. Write in flowing paragraphs only — no bullet points, no headings, no markdown. "
                + "Do not invent facts; rely only on what the articles say.\n\n"
                + "Articles:\n"
                + combinedArticles;

        return cleanResponse(sendPrompt(prompt));
    }

    /**
     * Summarizes arbitrary user text to a concise output.
     *
     * @param userText Raw user text.
     * @return Summarized text.
     */
    public String summarizeText(String userText) {
        if (userText == null || userText.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: Your text input is empty.",
                    "Paste your text and press Enter twice when done."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        }

        String prompt = "Summarize the following content clearly and thoroughly. "
                + "Keep roughly 35 to 45 percent of the original length, and never go below 200 words "
                + "(unless the source is shorter than that). "
                + "Write in flowing, well-developed paragraphs — minimum three paragraphs when the source is substantial. "
                + "Preserve key names, numbers, quotes, and cause-and-effect. "
                + "No bullet points, no headings, no markdown.\n\n"
                + "Text:\n"
                + userText;

        return cleanResponse(sendPrompt(prompt));
    }

    /**
     * Sends any custom prompt to Gemini and returns cleaned result text.
     *
     * @param prompt Prompt text to send.
     * @return Cleaned Gemini output.
     */
    public String generateFromPrompt(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            printErrorBox(
                    "ERROR: Prompt is empty.",
                    "Please provide a valid prompt."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        }

        return cleanResponse(sendPrompt(prompt));
    }

    /**
     * Sends prompt payload to Gemini and extracts candidates[0].content.parts[0].text.
     *
     * @param prompt Prompt text to send.
     * @return Gemini output text or fallback response.
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

            TerminalUtils.showLoading("Please wait");

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
                return cleanResponse(FALLBACK_RESPONSE);
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                handleGeminiHttpError(response.statusCode(), response.body());
                return cleanResponse(FALLBACK_RESPONSE);
            }

            String responseBody = response.body();
            if (responseBody == null || responseBody.trim().isEmpty()) {
                setLastFailureReason("Gemini returned an empty response.");
                printErrorBox(
                        "ERROR: Gemini returned an empty response.",
                        "Please try again in a moment."
                );
                return cleanResponse(FALLBACK_RESPONSE);
            }

            JSONObject root = new JSONObject(responseBody);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                setLastFailureReason("Gemini returned no candidates.");
                printErrorBox(
                        "ERROR: Gemini did not return any candidates.",
                        "Please try the request again."
                );
                return cleanResponse(FALLBACK_RESPONSE);
            }

            JSONObject firstCandidate = candidates.optJSONObject(0);
            if (firstCandidate == null) {
                setLastFailureReason("Gemini candidate format is invalid.");
                printErrorBox(
                        "ERROR: Gemini candidate format is invalid.",
                        "Please try again."
                );
                return cleanResponse(FALLBACK_RESPONSE);
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
                return cleanResponse(FALLBACK_RESPONSE);
            }

            return cleanResponse(text);
        } catch (HttpTimeoutException exception) {
            setLastFailureReason("Gemini request timed out.");
            printErrorBox(
                    "ERROR: Gemini request timed out.",
                    "Please check your internet and try again."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            setLastFailureReason("Gemini request was interrupted.");
            printErrorBox(
                    "ERROR: Gemini request was interrupted.",
                    "Please try again."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        } catch (IOException exception) {
            setLastFailureReason("Could not reach Gemini service.");
            printErrorBox(
                    "ERROR: Could not reach Gemini.",
                    "Please check your connection and try again."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        } catch (Exception exception) {
            setLastFailureReason("Gemini returned malformed data.");
            printErrorBox(
                    "ERROR: Gemini returned malformed data.",
                    "Please try again with a different input."
            );
            return cleanResponse(FALLBACK_RESPONSE);
        }
    }

    /**
     * Handles non-2xx Gemini responses and stores a concise failure reason.
     *
     * @param statusCode HTTP status code.
     * @param responseBody Raw response body text.
     * @return Nothing. It prints user-facing errors.
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

        if (statusCode >= 500 && statusCode <= 504) {
            setLastFailureReason(apiMessage.isEmpty()
                    ? "Gemini is temporarily overloaded (HTTP " + statusCode + ")."
                    : "Gemini is temporarily overloaded (HTTP " + statusCode + "): " + apiMessage);
            printErrorBox(
                    "ERROR: Gemini is temporarily overloaded (HTTP " + statusCode + ").",
                    "Please try again in a moment.",
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
     * Extracts a readable message from Gemini JSON error payload.
     *
     * @param responseBody Raw response body.
     * @return Error message text or empty string.
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
     * Shortens long API messages into compact single-line output.
     *
     * @param value Raw message text.
     * @param maxLength Maximum kept length.
     * @return Compact message text.
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
     * Prints retry wait information for HTTP 429 backoff intervals.
     *
     * @param waitSeconds Seconds before next retry.
     * @return Nothing. It prints directly to terminal.
     */
    private void printRateLimitRetryBox(int waitSeconds) {
        List<String> lines = new ArrayList<>();
        lines.add("Rate limit hit. Retrying in " + waitSeconds + "s...");
        TerminalUtils.printSimpleBox(lines, YELLOW + BOLD, YELLOW, RESET);
    }

    /**
     * Stores the latest Gemini failure reason for UI fallback notices.
     *
     * @param reason Failure reason text.
     * @return Nothing. It updates in-memory state.
     */
    private void setLastFailureReason(String reason) {
        this.lastFailureReason = reason == null ? "" : reason.trim();
    }

    /**
     * Cleans markdown artifacts from Gemini output before returning to UI.
     *
     * @param text Raw Gemini output text.
     * @return Cleaned plain-text output.
     */
    private String cleanResponse(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = text;
        cleaned = cleaned.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        cleaned = cleaned.replaceAll("(?m)^\\s*\\*\\s+", "- ");
        cleaned = cleaned.replaceAll("(?m)^\\s*#+\\s*", "");
        cleaned = cleaned.replace("`", "");
        cleaned = cleaned.replaceAll("[ \\t]+\\n", "\n");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
    }

    /**
     * Prints a red bordered error message with shared terminal layout.
     *
     * @param messageLines Error lines to print.
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
}