package com.iadanza.profpublicationsapp.application.service;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.Professor;

import java.util.List;
import java.util.Optional;

/**
 * Espone i casi d'uso applicativi legati alla ricerca dei professori.
 * Questo layer orchestrerà in seguito uno o più connector infrastrutturali.
 */
public interface ProfessorSearchService {

    List<Professor> searchByFreeText(String query);

    Optional<Professor> findByIdentifier(IdentifierType identifierType, String value);
}