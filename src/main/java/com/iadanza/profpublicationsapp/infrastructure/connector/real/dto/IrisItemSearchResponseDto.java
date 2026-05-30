package com.iadanza.profpublicationsapp.infrastructure.connector.real.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO della risposta REST IRIS items/search.
 *
 * Alcune installazioni IRIS/CINECA possono restituire la lista degli item
 * con nomi leggermente diversi o non standard.
 * Per questo motivo vengono accettati più alias Jackson.
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