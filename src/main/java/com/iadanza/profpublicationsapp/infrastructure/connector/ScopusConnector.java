package com.iadanza.profpublicationsapp.infrastructure.connector;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;
import java.util.Optional;

/**
 * Contratto per l'accesso a Scopus come sorgente bibliografica
 * e citazionale ufficiale.
 */
public interface ScopusConnector {

    List<Professor> searchProfessors(String query);

    Optional<Professor> findProfessorByIdentifier(IdentifierType identifierType, String value);

    List<Publication> findPublicationsByProfessor(Professor professor);

    Optional<CitationSummary> fetchCitationSummary(Publication publication);

    List<CitingDocument> findCitingDocuments(Publication publication);
}