package com.iadanza.profpublicationsapp.application.service.impl;

import com.iadanza.profpublicationsapp.application.service.ProfessorSearchService;
import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;

import java.util.Optional;

/**
 * Implementazione del servizio di ricerca professori.
 *
 * La ricerca usa IRIS come sorgente istituzionale primaria
 * e avviene tramite identificativi reali.
 */
public class DefaultProfessorSearchService implements ProfessorSearchService {

    private final IrisConnector irisConnector;

    public DefaultProfessorSearchService(IrisConnector irisConnector) {
        this.irisConnector = irisConnector;
    }

    @Override
    public Optional<Professor> findByIdentifier(IdentifierType identifierType, String value) {
        return irisConnector.findProfessorByIdentifier(identifierType, value);
    }
}