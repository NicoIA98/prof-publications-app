package com.iadanza.profpublicationsapp.infrastructure.json;

import java.util.List;

/**
 * DTO JSON radice per il mock IRIS di un professore con le sue pubblicazioni.
 */
public record IrisProfessorPublicationsJson(
        IrisProfessorJson professor,
        String lastRefreshAt,
        List<IrisPublicationJson> publications
) {
}