package com.iadanza.profpublicationsapp.infrastructure.json;

/**
 * DTO JSON per un identificativo esterno presente nei dati mock IRIS.
 */
public record IrisExternalIdentifierJson(
        String type,
        String value,
        String sourceType
) {
}