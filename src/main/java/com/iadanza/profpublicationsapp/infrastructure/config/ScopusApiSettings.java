package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Configurazione runtime per l'integrazione Scopus / Elsevier.
 *
 * Le credenziali reali devono arrivare da .env o variabili d'ambiente,
 * mai da codice sorgente o file versionati.
 *
 * Fase E — Scopus reale minima:
 * - API key opzionale;
 * - institutional token opzionale;
 * - base URL configurabile;
 * - timeout configurabile;
 * - degradazione controllata se la API key manca.
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