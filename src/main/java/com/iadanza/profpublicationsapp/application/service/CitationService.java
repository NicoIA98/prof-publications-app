package com.iadanza.profpublicationsapp.application.service;

import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;

/**
 * Gestisce i casi d'uso applicativi relativi alle citazioni
 * di una specifica pubblicazione.
 */
public interface CitationService {
    CitationSummary getCachedCitationSummary(Publication publication);
    List<CitingDocument> getCachedCitingDocuments(Publication publication);

    CitationSummary refreshCitationSummary(Publication publication);
    List<CitingDocument> refreshCitingDocuments(Publication publication);
}