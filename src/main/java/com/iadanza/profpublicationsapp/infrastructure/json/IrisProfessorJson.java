package com.iadanza.profpublicationsapp.infrastructure.json;

import java.util.List;

/**
 * DTO JSON per il professore contenuto nel file mock IRIS.
 */
public record IrisProfessorJson(
        String firstName,
        String lastName,
        String fullName,
        String affiliation,
        List<IrisExternalIdentifierJson> externalIdentifiers
) {
}