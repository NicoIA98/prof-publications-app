package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Configurazione runtime per l'integrazione Google Scholar tramite SerpApi.
 *
 * Ordine lettura:
 * 1. settings.properties locale, se presente;
 * 2. variabili d'ambiente / .env;
 * 3. default applicativi.
 *
 * Nota:
 * non impostiamo un limite configurabile ai documenti citanti Scholar.
 * Il connector recupera tutte le pagine esposte da SerpApi/Scholar.
 */
public record SerpApiScholarSettings(
        String apiKey,
        String baseUrl,
        int timeoutSeconds
) {

    private static final String DEFAULT_BASE_URL = "https://serpapi.com/search";
    private static final int DEFAULT_TIMEOUT_SECONDS = 45;

    public SerpApiScholarSettings {
        apiKey = normalize(apiKey);
        baseUrl = normalizeBaseUrl(baseUrl);
        timeoutSeconds = normalizeTimeout(timeoutSeconds);
    }

    public static SerpApiScholarSettings fromEnvironment() {
        String apiKey = DotenvLoader.getOrDefault(
                "SERPAPI_API_KEY",
                ""
        );

        String baseUrl = DotenvLoader.getOrDefault(
                "SERPAPI_BASE_URL",
                DEFAULT_BASE_URL
        );

        int timeoutSeconds = DotenvLoader.getIntOrDefault(
                "SERPAPI_TIMEOUT_SECONDS",
                DEFAULT_TIMEOUT_SECONDS
        );

        return new SerpApiScholarSettings(
                apiKey,
                baseUrl,
                timeoutSeconds
        );
    }

    public static SerpApiScholarSettings fromLocalSettingsWithEnvironmentFallback(
            ConnectionSettings localSettings,
            boolean localSettingsFileExists
    ) {
        if (localSettings == null) {
            return fromEnvironment();
        }

        String apiKey = firstText(
                localSettings.serpApiApiKey(),
                DotenvLoader.getOrDefault("SERPAPI_API_KEY", "")
        );

        String baseUrl = resolveString(
                localSettings.serpApiBaseUrl(),
                localSettingsFileExists,
                "SERPAPI_BASE_URL",
                DEFAULT_BASE_URL
        );

        int timeoutSeconds = resolveInt(
                localSettings.serpApiTimeoutSeconds(),
                localSettingsFileExists,
                "SERPAPI_TIMEOUT_SECONDS",
                DEFAULT_TIMEOUT_SECONDS
        );

        return new SerpApiScholarSettings(
                apiKey,
                baseUrl,
                timeoutSeconds
        );
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String maskedApiKey() {
        return maskSecret(apiKey);
    }

    private static String resolveString(
            String localValue,
            boolean localSettingsFileExists,
            String environmentKey,
            String defaultValue
    ) {
        if (localSettingsFileExists && hasText(localValue)) {
            return localValue;
        }

        return DotenvLoader.getOrDefault(environmentKey, defaultValue);
    }

    private static int resolveInt(
            int localValue,
            boolean localSettingsFileExists,
            String environmentKey,
            int defaultValue
    ) {
        if (localSettingsFileExists && localValue > 0) {
            return localValue;
        }

        return DotenvLoader.getIntOrDefault(environmentKey, defaultValue);
    }

    private static String firstText(String firstValue, String fallbackValue) {
        if (hasText(firstValue)) {
            return firstValue;
        }

        return fallbackValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = normalize(value);

        if (normalized.isBlank()) {
            return DEFAULT_BASE_URL;
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private static int normalizeTimeout(int value) {
        if (value <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }

        return value;
    }

    private static String maskSecret(String value) {
        String normalized = normalize(value);

        if (normalized.isBlank()) {
            return "";
        }

        if (normalized.length() <= 6) {
            return "***";
        }

        return normalized.substring(0, 3)
                + "***"
                + normalized.substring(normalized.length() - 3);
    }
}