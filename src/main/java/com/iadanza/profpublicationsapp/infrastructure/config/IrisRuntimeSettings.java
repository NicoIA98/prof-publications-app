package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Configurazione runtime del connettore IRIS reale.
 */
public record IrisRuntimeSettings(
        String baseUrl,
        IrisAccessMode accessMode,
        String restSearchPath,
        String oaiIdentifyPath,
        int timeoutSeconds,
        boolean verifySsl
) {
}