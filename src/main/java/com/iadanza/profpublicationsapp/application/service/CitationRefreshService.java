package com.iadanza.profpublicationsapp.application.service;

import com.iadanza.profpublicationsapp.domain.model.Professor;

/**
 * Gestisce il refresh esplicito dei dati citazionali provenienti
 * dalle sorgenti esterne, senza modificare il catalogo canonico
 * delle pubblicazioni derivate da IRIS.
 */
public interface CitationRefreshService {

    void refreshScopusData(Professor professor);

    void refreshScholarData(Professor professor);

    void refreshAllCitationData(Professor professor);
}