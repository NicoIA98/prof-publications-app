package com.iadanza.profpublicationsapp.infrastructure.connector.real.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Item/publication restituito da items/search.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IrisItemDto(
        String type,
        String name,
        String handle,
        Integer itemId,
        String submitterNetID,
        Map<String, List<IrisMetadataValueDto>> metadata
) {
}