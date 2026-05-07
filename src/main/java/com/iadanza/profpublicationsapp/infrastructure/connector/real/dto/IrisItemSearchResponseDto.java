package com.iadanza.profpublicationsapp.infrastructure.connector.real.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO della risposta REST IRIS items/search.
 *
 * Nota:
 * alcune installazioni IRIS/CINECA restituiscono il campo con nome:
 * "restResourseDTOList"
 * con "Resourse" scritto in modo non standard.
 *
 * Per sicurezza accettiamo più alias.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IrisItemSearchResponseDto(
        @JsonProperty("limit")
        Integer limit,

        @JsonProperty("offset")
        Integer offset,

        @JsonProperty("count")
        Integer count,

        @JsonProperty("restResourseDTOList")
        @JsonAlias({
                "restResourceDTOList",
                "restResourceDtoList",
                "restResourseDtoList"
        })
        List<IrisItemDto> restResourceDtoList
) {
}