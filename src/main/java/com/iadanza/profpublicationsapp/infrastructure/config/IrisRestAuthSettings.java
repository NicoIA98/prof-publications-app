package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Configurazione per chiamate REST autenticate verso IRIS/CINECA.
 */
public record IrisRestAuthSettings(
        String baseUrl,
        String pathIR,
        String pathRM,
        String username,
        String password,
        int timeoutSeconds
) {
    public boolean isConfigured() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}