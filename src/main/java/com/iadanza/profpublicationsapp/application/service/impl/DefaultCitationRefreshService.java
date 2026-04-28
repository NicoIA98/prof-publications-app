package com.iadanza.profpublicationsapp.application.service.impl;

import com.iadanza.profpublicationsapp.application.service.CitationRefreshService;
import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;

/**
 * Implementazione base del refresh citazionale su richiesta utente.
 */
public class DefaultCitationRefreshService implements CitationRefreshService {

    private final PublicationCatalogService publicationCatalogService;
    private final DefaultCitationService citationService;

    public DefaultCitationRefreshService(
            PublicationCatalogService publicationCatalogService,
            DefaultCitationService citationService
    ) {
        this.publicationCatalogService = publicationCatalogService;
        this.citationService = citationService;
    }

    @Override
    public void refreshScopusData(Professor professor) {
        List<Publication> publications = publicationCatalogService.getCachedPublications(professor);

        for (Publication publication : publications) {
            citationService.refreshScopusCitationSummary(publication);
            citationService.refreshScopusCitingDocuments(publication);
        }
    }

    @Override
    public void refreshScholarData(Professor professor) {
        List<Publication> publications = publicationCatalogService.getCachedPublications(professor);

        for (Publication publication : publications) {
            citationService.refreshScholarCitationSummary(publication);
            citationService.refreshScholarCitingDocuments(publication);
        }
    }

    @Override
    public void refreshAllCitationData(Professor professor) {
        List<Publication> publications = publicationCatalogService.getCachedPublications(professor);

        for (Publication publication : publications) {
            citationService.refreshCitationSummary(publication);
            citationService.refreshCitingDocuments(publication);
        }
    }
}