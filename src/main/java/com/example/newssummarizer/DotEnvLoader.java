package com.example.newssummarizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DotEnvLoader {

    // Store .env values safely for Java 25 without reflective env mutation.
    private static final Map<String, String> DOT_ENV_OVERRIDES = new HashMap<>();

    public static void load() {
        Path envFile = Path.of(".env");
        if (!Files.exists(envFile)) {
            envFile = Path.of("src/main/java/com/example/newssummarizer/.env");
        }
        if (!Files.exists(envFile)) return;

        try {
            Map<String, String> vars = new HashMap<>();
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 1) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim().replaceAll("^\"|\"$|^'|'$", "");
                vars.put(key, val);
            }
            // Cache parsed values for lookup via getEnv().
            DOT_ENV_OVERRIDES.putAll(vars);
        } catch (IOException e) {
            // silently skip — services will report missing keys
        }
    }

    // Prefer .env overrides, fall back to real environment variables.
    public static String getEnv(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        String trimmedKey = key.trim();
        if (DOT_ENV_OVERRIDES.containsKey(trimmedKey)) {
            return DOT_ENV_OVERRIDES.get(trimmedKey);
        }
        return System.getenv(trimmedKey);
    }
}
