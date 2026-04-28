package com.iadanza.profpublicationsapp.application.service.impl;

import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.persistence.PublicationCacheRepository;

import java.util.List;

/**
 * Implementazione base del catalogo pubblicazioni.
 * Le pubblicazioni sono considerate canoniche se derivate da IRIS.
 *
 * In questa versione la cache è persistita su SQLite.
 */
public class DefaultPublicationCatalogService implements PublicationCatalogService {

    private final IrisConnector irisConnector;
    private final PublicationCacheRepository publicationCacheRepository;

    public DefaultPublicationCatalogService(
            IrisConnector irisConnector,
            PublicationCacheRepository publicationCacheRepository
    ) {
        this.irisConnector = irisConnector;
        this.publicationCacheRepository = publicationCacheRepository;
    }

    @Override
    public List<Publication> getCachedPublications(Professor professor) {
        return publicationCacheRepository.findCachedPublications(professor);
    }

    @Override
    public List<Publication> refreshPublicationsFromIris(Professor professor) {
        List<Publication> refreshed = irisConnector.fetchProfessorPublications(professor);
        publicationCacheRepository.savePublications(professor, refreshed);
        return refreshed;
    }
}