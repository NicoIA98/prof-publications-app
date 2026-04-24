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

    CitationSummary getCitationSummary(Publication publication);

    List<CitingDocument> getCitingDocuments(Publication publication);
}