package com.iadanza.profpublicationsapp.infrastructure.connector.real.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Risposta di items/search.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IrisItemSearchResponseDto(
        Integer limit,
        Integer offset,
        Integer count,
        @JsonProperty("restResourseDTOList")
        List<IrisItemDto> restResourceDtoList
) {
}