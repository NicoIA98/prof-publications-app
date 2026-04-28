package com.iadanza.profpublicationsapp.infrastructure.json;

import java.util.List;

/**
 * DTO JSON per una pubblicazione presente nei dati mock IRIS.
 */
public record IrisPublicationJson(
        String title,
        List<String> authors,
        Integer year,
        String venue,
        String doi,
        String abstractText,
        String sourceUrl,
        String recordStatus,
        List<IrisExternalIdentifierJson> externalIdentifiers
) {
}