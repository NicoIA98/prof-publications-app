package com.iadanza.profpublicationsapp.application.service.impl;

import com.iadanza.profpublicationsapp.application.service.ProfessorSearchService;
import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;

import java.util.List;
import java.util.Optional;

/**
 * Implementazione base del servizio di ricerca professori.
 * In questa fase iniziale la ricerca usa IRIS come sorgente principale.
 */
public class DefaultProfessorSearchService implements ProfessorSearchService {

    private final IrisConnector irisConnector;

    public DefaultProfessorSearchService(IrisConnector irisConnector) {
        this.irisConnector = irisConnector;
    }

    @Override
    public List<Professor> searchByFreeText(String query) {
        return irisConnector.searchProfessors(query);
    }

    @Override
    public Optional<Professor> findByIdentifier(IdentifierType identifierType, String value) {
        return irisConnector.findProfessorByIdentifier(identifierType, value);
    }
}