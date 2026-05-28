package com.iadanza.profpublicationsapp.infrastructure.connector;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;
import java.util.Optional;

/**
 * Contratto per l'accesso ai dati istituzionali IRIS.
 * IRIS è la sorgente canonica delle pubblicazioni del professore.
 */
public interface IrisConnector {

    Optional<Professor> findProfessorByIdentifier(IdentifierType identifierType, String value);

    List<Publication> fetchProfessorPublications(Professor professor);

    Optional<BibtexEntry> fetchBibtexEntry(Publication publication);
}