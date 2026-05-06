package com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic;

/**
 * Risultato di un singolo test su un endpoint REST IRIS/CINECA.
 */
public record RestEndpointProbeResult(
        String method,
        String path,
        int statusCode,
        String locationHeader,
        String contentType,
        String bodyPreview,
        boolean endpointExistsLikely,
        boolean authLikelyRequired,
        boolean redirected,
        String notes
) {
}