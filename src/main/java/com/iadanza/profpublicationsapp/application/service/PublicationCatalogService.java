package com.iadanza.profpublicationsapp.application.service;

import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;

/**
 * Servizio applicativo responsabile della gestione del catalogo
 * delle pubblicazioni canoniche del professore.
 *
 * Le pubblicazioni vengono considerate ufficialmente derivate da IRIS,
 * mentre le altre sorgenti arricchiscono i dati citazionali.
 */
public interface PublicationCatalogService {
    List<Publication> getCachedPublications(Professor professor);
    List<Publication> refreshPublicationsFromIris(Professor professor);
}