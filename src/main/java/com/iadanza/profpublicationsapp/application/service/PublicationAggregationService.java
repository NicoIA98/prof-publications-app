package com.iadanza.profpublicationsapp.application.service;

import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;

/**
 * Servizio applicativo responsabile del recupero e dell'unificazione
 * delle pubblicazioni provenienti da più sorgenti.
 */
public interface PublicationAggregationService {

    List<Publication> getAggregatedPublicationsForProfessor(Professor professor);
}