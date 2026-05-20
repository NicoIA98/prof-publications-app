package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Configurazione runtime per l'integrazione Scopus / Elsevier.
 *
 * Ordine lettura:
 * 1. settings.properties locale, se presente;
 * 2. variabili d'ambiente / .env;
 * 3. default applicativi.
 *
 * Le credenziali reali non devono mai essere committate
 * e non devono mai essere stampate in chiaro nei log.
 */
public record ScopusApiSettings(
        String apiKey,
        String instToken,
        String baseUrl,
        int timeoutSeconds
) {

    private static final String DEFAULT_BASE_URL = "https://api.elsevier.com";
    private static final int DEFAULT_TIMEOUT_SECONDS = 45;

    public ScopusApiSettings {
        apiKey = normalize(apiKey);
        instToken = normalize(instToken);
        baseUrl = normalizeBaseUrl(baseUrl);
        timeoutSeconds = normalizeTimeout(timeoutSeconds);
    }

    public static ScopusApiSettings fromEnvironment() {
        String apiKey = DotenvLoader.getOrDefault(
                "SCOPUS_API_KEY",
                ""
        );

        String instToken = DotenvLoader.getOrDefault(
                "SCOPUS_INST_TOKEN",
                ""
        );

        String baseUrl = DotenvLoader.getOrDefault(
                "SCOPUS_BASE_URL",
                DEFAULT_BASE_URL
        );

        int timeoutSeconds = DotenvLoader.getIntOrDefault(
                "SCOPUS_TIMEOUT_SECONDS",
                DEFAULT_TIMEOUT_SECONDS
        );

        return new ScopusApiSettings(
                apiKey,
                instToken,
                baseUrl,
                timeoutSeconds
        );
    }

    public static ScopusApiSettings fromLocalSettingsWithEnvironmentFallback(
            ConnectionSettings localSettings,
            boolean localSettingsFileExists
    ) {
        if (localSettings == null) {
            return fromEnvironment();
        }

        String apiKey = firstText(
                localSettings.scopusApiKey(),
                DotenvLoader.getOrDefault("SCOPUS_API_KEY", "")
        );

        String instToken = firstText(
                localSettings.scopusInstToken(),
                DotenvLoader.getOrDefault("SCOPUS_INST_TOKEN", "")
        );

        String baseUrl = resolveString(
                localSettings.scopusBaseUrl(),
                localSettingsFileExists,
                "SCOPUS_BASE_URL",
                DEFAULT_BASE_URL
        );

        int timeoutSeconds = resolveInt(
                localSettings.scopusTimeoutSeconds(),
                localSettingsFileExists,
                "SCOPUS_TIMEOUT_SECONDS",
                DEFAULT_TIMEOUT_SECONDS
        );

        return new ScopusApiSettings(
                apiKey,
                instToken,
                baseUrl,
                timeoutSeconds
        );
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean hasInstToken() {
        return instToken != null && !instToken.isBlank();
    }

    public boolean isEnabled() {
        return hasApiKey();
    }

    public String maskedApiKey() {
        return maskSecret(apiKey);
    }

    public String maskedInstToken() {
        return maskSecret(instToken);
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