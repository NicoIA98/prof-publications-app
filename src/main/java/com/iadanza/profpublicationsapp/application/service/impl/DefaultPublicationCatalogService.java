package com.iadanza.profpublicationsapp.application.service.impl;

import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementazione base del catalogo pubblicazioni.
 * Le pubblicazioni sono considerate canoniche se derivate da IRIS.
 *
 * In questa prima versione la cache è solo in memoria.
 * Più avanti verrà sostituita o affiancata da persistenza SQLite.
 */
public class DefaultPublicationCatalogService implements PublicationCatalogService {

    private final IrisConnector irisConnector;
    private final Map<String, List<Publication>> publicationCache = new HashMap<>();

    public DefaultPublicationCatalogService(IrisConnector irisConnector) {
        this.irisConnector = irisConnector;
    }

    @Override
    public List<Publication> getCachedPublications(Professor professor) {
        return publicationCache.getOrDefault(buildProfessorKey(professor), List.of());
    }

    @Override
    public List<Publication> refreshPublicationsFromIris(Professor professor) {
        List<Publication> refreshed = irisConnector.fetchProfessorPublications(professor);
        publicationCache.put(buildProfessorKey(professor), refreshed);
        return refreshed;
    }

    private String buildProfessorKey(Professor professor) {
        return (professor.fullName() + "|" + professor.affiliation()).toLowerCase();
    }
}