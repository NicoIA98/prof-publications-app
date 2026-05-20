package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Configurazione runtime per l'integrazione Google Scholar tramite SerpApi.
 *
 * Fase F1:
 * - legge API key e parametri da .env;
 * - permette fallback automatico a FakeScholarConnector se la key manca;
 * - non stampa mai la API key nei log.
 *
 * Nota:
 * non impostiamo un limite configurabile ai documenti citanti Scholar.
 * La gestione della paginazione verrà fatta nel connector/probe.
 */
public record SerpApiScholarSettings(
        String apiKey,
        String baseUrl,
        int timeoutSeconds
) {

    public static SerpApiScholarSettings fromEnvironment() {
        String apiKey = DotenvLoader.getOrDefault(
                "SERPAPI_API_KEY",
                ""
        );

        String baseUrl = DotenvLoader.getOrDefault(
                "SERPAPI_BASE_URL",
                "https://serpapi.com/search"
        );

        int timeoutSeconds = DotenvLoader.getIntOrDefault(
                "SERPAPI_TIMEOUT_SECONDS",
                45
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
}