package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Configurazione per chiamate REST autenticate verso IRIS/CINECA.
 *
 * Ordine lettura:
 * 1. settings.properties locale, se presente;
 * 2. variabili d'ambiente / .env;
 * 3. default applicativi.
 */
public record IrisRestAuthSettings(
        String baseUrl,
        String pathIR,
        String pathRM,
        String username,
        String password,
        int timeoutSeconds
) {

    private static final String DEFAULT_BASE_URL = "https://iris.unicas.it:443/";
    private static final String DEFAULT_PATH_IR = "rest/api/v1/";
    private static final String DEFAULT_PATH_RM = "rm/restservices/api/v1";
    private static final String DEFAULT_USERNAME = "restadmin";
    private static final int DEFAULT_TIMEOUT_SECONDS = 45;

    public IrisRestAuthSettings {
        baseUrl = normalizeOrDefault(baseUrl, DEFAULT_BASE_URL);
        pathIR = normalizeOrDefault(pathIR, DEFAULT_PATH_IR);
        pathRM = normalizeOrDefault(pathRM, DEFAULT_PATH_RM);
        username = normalize(username);
        password = normalize(password);
        timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    public static IrisRestAuthSettings fromLocalSettingsWithEnvironmentFallback(
            ConnectionSettings localSettings,
            boolean localSettingsFileExists
    ) {
        if (localSettings == null) {
            return fromEnvironment();
        }

        String baseUrl = resolveString(
                localSettings.irisRestBaseUrl(),
                localSettingsFileExists,
                "IRIS_REST_BASE_URL",
                DEFAULT_BASE_URL
        );

        String pathIr = resolveString(
                localSettings.irisRestPathIr(),
                localSettingsFileExists,
                "IRIS_REST_PATH_IR",
                DEFAULT_PATH_IR
        );

        String pathRm = resolveString(
                localSettings.irisRestPathRm(),
                localSettingsFileExists,
                "IRIS_REST_PATH_RM",
                DEFAULT_PATH_RM
        );

        String username = firstText(
                localSettings.irisRestUsername(),
                DotenvLoader.getOrDefault("IRIS_REST_USERNAME", DEFAULT_USERNAME)
        );

        String password = firstText(
                localSettings.irisRestPassword(),
                DotenvLoader.getOrDefault("IRIS_REST_PASSWORD", "")
        );

        int timeoutSeconds = resolveInt(
                localSettings.irisRestTimeoutSeconds(),
                localSettingsFileExists,
                "IRIS_REST_TIMEOUT_SECONDS",
                DEFAULT_TIMEOUT_SECONDS
        );

        return new IrisRestAuthSettings(
                baseUrl,
                pathIr,
                pathRm,
                username,
                password,
                timeoutSeconds
        );
    }

    public static IrisRestAuthSettings fromEnvironment() {
        return new IrisRestAuthSettings(
                DotenvLoader.getOrDefault("IRIS_REST_BASE_URL", DEFAULT_BASE_URL),
                DotenvLoader.getOrDefault("IRIS_REST_PATH_IR", DEFAULT_PATH_IR),
                DotenvLoader.getOrDefault("IRIS_REST_PATH_RM", DEFAULT_PATH_RM),
                DotenvLoader.getOrDefault("IRIS_REST_USERNAME", DEFAULT_USERNAME),
                DotenvLoader.getOrDefault("IRIS_REST_PASSWORD", ""),
                DotenvLoader.getIntOrDefault("IRIS_REST_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS)
        );
    }

    public boolean isConfigured() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
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
        return value != null ? value.trim() : "";
    }

    private static String normalizeOrDefault(String value, String defaultValue) {
        String normalized = normalize(value);
        return normalized.isBlank() ? defaultValue : normalized;
    }
}