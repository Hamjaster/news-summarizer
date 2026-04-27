package com.example.newssummarizer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DotEnvLoader {

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
            injectIntoEnv(vars);
        } catch (IOException e) {
            // silently skip — services will report missing keys
        }
    }

    @SuppressWarnings("unchecked")
    private static void injectIntoEnv(Map<String, String> vars) {
        try {
            Map<String, String> env = System.getenv();
            Field field = env.getClass().getDeclaredField("m");
            field.setAccessible(true);
            ((Map<String, String>) field.get(env)).putAll(vars);
        } catch (Exception e) {
            // reflection blocked — user must export vars manually
        }
    }
}
