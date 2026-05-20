package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Modello locale delle impostazioni di connessione inserite dall'utente.
 *
 * Questi dati vengono salvati in:
 * user.home/.prof-publications-app/settings.properties
 *
 * Non devono mai essere committati e non devono mai essere stampati nei log.
 */
public record ConnectionSettings(
        String irisRestBaseUrl,
        String irisRestPathIr,
        String irisRestPathRm,
        String irisRestUsername,
        String irisRestPassword,
        int irisRestTimeoutSeconds,

        String scopusBaseUrl,
        String scopusApiKey,
        String scopusInstToken,
        int scopusTimeoutSeconds,

        String serpApiBaseUrl,
        String serpApiApiKey,
        int serpApiTimeoutSeconds
) {

    public static ConnectionSettings empty() {
        return new ConnectionSettings(
                "https://iris.unicas.it:443/",
                "rest/api/v1/",
                "rm/restservices/api/v1",
                "",
                "",
                45,

                "https://api.elsevier.com",
                "",
                "",
                45,

                "https://serpapi.com/search",
                "",
                45
        );
    }

    public boolean hasIrisCredentials() {
        return hasText(irisRestUsername) && hasText(irisRestPassword);
    }

    public boolean hasScopusApiKey() {
        return hasText(scopusApiKey);
    }

    public boolean hasSerpApiApiKey() {
        return hasText(serpApiApiKey);
    }

    public boolean hasAnyMissingMainCredential() {
        return !hasIrisCredentials()
                || !hasScopusApiKey()
                || !hasSerpApiApiKey();
    }

    public ConnectionSettings normalized() {
        return new ConnectionSettings(
                normalizeOrDefault(irisRestBaseUrl, "https://iris.unicas.it:443/"),
                normalizeOrDefault(irisRestPathIr, "rest/api/v1/"),
                normalizeOrDefault(irisRestPathRm, "rm/restservices/api/v1"),
                normalize(irisRestUsername),
                normalize(irisRestPassword),
                positiveOrDefault(irisRestTimeoutSeconds, 45),

                normalizeOrDefault(scopusBaseUrl, "https://api.elsevier.com"),
                normalize(scopusApiKey),
                normalize(scopusInstToken),
                positiveOrDefault(scopusTimeoutSeconds, 45),

                normalizeOrDefault(serpApiBaseUrl, "https://serpapi.com/search"),
                normalize(serpApiApiKey),
                positiveOrDefault(serpApiTimeoutSeconds, 45)
        );
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

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}