package com.iadanza.profpublicationsapp.bootstrap;

import com.iadanza.profpublicationsapp.application.service.BibtexService;
import com.iadanza.profpublicationsapp.application.service.CitationService;
import com.iadanza.profpublicationsapp.application.service.ProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.infrastructure.lookup.ProfessorLookupRepository;

/**
 * Contenitore dei servizi principali usati dalla UI.
 *
 * Serve a non costruire connector, repository e service direttamente
 * dentro ProfessorPublicationsApp.
 */
public record AppServices(
        ProfessorSearchService professorSearchService,
        PublicationCatalogService publicationCatalogService,
        CitationService citationService,
        BibtexService bibtexService,
        ProfessorLookupRepository professorLookupRepository
) {
}