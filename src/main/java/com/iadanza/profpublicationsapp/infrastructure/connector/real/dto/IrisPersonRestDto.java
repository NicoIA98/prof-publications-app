package com.iadanza.profpublicationsapp.infrastructure.connector.real.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO minimale della persona restituita da personsbyrpid.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IrisPersonRestDto(
        String type,
        Integer id,
        String crisId,
        String firstName,
        String lastName
) {
}