package com.iadanza.profpublicationsapp.application.service;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.Professor;

import java.util.Optional;

/**
 * Espone i casi d'uso applicativi legati alla ricerca dei professori.
 *
 * Versione finale demo:
 * - la ricerca avviene tramite identificativi reali;
 * - non viene più esposta ricerca testuale libera basata su dati fake/mock.
 */
public interface ProfessorSearchService {

    Optional<Professor> findByIdentifier(IdentifierType identifierType, String value);
}