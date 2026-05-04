package com.iadanza.profpublicationsapp.infrastructure.connector.real.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Singolo valore metadata restituito dalla REST IRIS.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IrisMetadataValueDto(
        String value,
        String authority,
        String place
) {
}