package com.iadanza.profpublicationsapp.infrastructure.connector.real;

/**
 * Risultato di una chiamata REST autenticata verso IRIS/CINECA.
 */
public record AuthenticatedRestCallResult(
        String method,
        String path,
        int statusCode,
        String contentType,
        String bodyPreview,
        String notes
) {
}