package com.iadanza.profpublicationsapp.infrastructure.connector;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;
import java.util.Optional;

/**
 * Contratto per l'accesso ai dati istituzionali IRIS.
 * IRIS viene trattata come sorgente istituzionale primaria.
 */
public interface IrisConnector {

    List<Professor> searchProfessors(String query);

    Optional<Professor> findProfessorByIdentifier(IdentifierType identifierType, String value);

    List<Publication> findPublicationsByProfessor(Professor professor);
}