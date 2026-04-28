package com.iadanza.profpublicationsapp.infrastructure.connector;

import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;
import java.util.Optional;

/**
 * Contratto per l'accesso a Scopus come sorgente ufficiale
 * di dati citazionali e documenti citanti.
 */
public interface ScopusConnector {

    Optional<CitationSummary> fetchCitationSummary(Publication publication);

    List<CitingDocument> findCitingDocuments(Publication publication);
}