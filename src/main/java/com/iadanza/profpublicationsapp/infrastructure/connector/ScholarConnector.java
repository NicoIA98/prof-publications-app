package com.iadanza.profpublicationsapp.infrastructure.connector;

import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.domain.model.ScholarAuthorMapping;

import java.util.List;
import java.util.Optional;

/**
 * Contratto per l'accesso ai dati Scholar tramite integrazioni affidabili
 * come SerpApi, senza scraping diretto custom.
 */
public interface ScholarConnector {

    Optional<ScholarAuthorMapping> findScholarAuthorMapping(Professor professor);

    List<Publication> findPublicationsByProfessor(Professor professor);

    List<Publication> findPublicationsByScholarAuthorId(String scholarAuthorId);

    Optional<CitationSummary> fetchCitationSummary(Publication publication);

    List<CitingDocument> findCitingDocuments(Publication publication);

    Optional<BibtexEntry> fetchBibtexEntry(Publication publication);
}