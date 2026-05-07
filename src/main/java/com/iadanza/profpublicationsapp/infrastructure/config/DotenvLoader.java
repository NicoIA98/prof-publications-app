package com.iadanza.profpublicationsapp.infrastructure.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loader minimale per file .env locale.
 *
 * Strategia:
 * 1. prima controlla le variabili d'ambiente del sistema;
 * 2. poi controlla il file .env nella root del progetto;
 * 3. non stampa mai valori sensibili.
 */
public final class DotenvLoader {

    private static final Path DOTENV_PATH = Path.of(".env");
    private static Map<String, String> cachedValues;

    private DotenvLoader() {
    }

    public static Optional<String> get(String key) {
        String systemValue = System.getenv(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return Optional.of(systemValue.trim());
        }

        Map<String, String> dotenvValues = loadDotenvValues();
        String dotenvValue = dotenvValues.get(key);

        if (dotenvValue != null && !dotenvValue.isBlank()) {
            return Optional.of(dotenvValue.trim());
        }

        return Optional.empty();
    }

    public static String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    public static int getIntOrDefault(String key, int defaultValue) {
        Optional<String> value = get(key);

        if (value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.get());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Map<String, String> loadDotenvValues() {
        if (cachedValues != null) {
            return cachedValues;
        }

        Map<String, String> values = new HashMap<>();

        if (!Files.exists(DOTENV_PATH)) {
            cachedValues = values;
            return cachedValues;
        }

        try {
            for (String line : Files.readAllLines(DOTENV_PATH)) {
                String trimmed = line.trim();

                if (trimmed.isBlank() || trimmed.startsWith("#")) {
                    continue;
                }

                int equalsIndex = trimmed.indexOf('=');

                if (equalsIndex <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, equalsIndex).trim();
                String value = trimmed.substring(equalsIndex + 1).trim();

                value = stripOptionalQuotes(value);

                if (!key.isBlank()) {
                    values.put(key, value);
                }
            }
        } catch (IOException e) {
            cachedValues = Map.of();
            return cachedValues;
        }

        cachedValues = values;
        return cachedValues;
    }

    private static String stripOptionalQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }

        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }
}