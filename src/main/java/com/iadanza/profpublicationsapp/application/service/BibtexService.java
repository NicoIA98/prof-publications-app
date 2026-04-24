package com.iadanza.profpublicationsapp.application.service;

import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;
import java.util.Optional;

/**
 * Servizio applicativo per la generazione o il recupero
 * di entry BibTeX a partire da una o più pubblicazioni.
 */
public interface BibtexService {

    Optional<BibtexEntry> resolveBibtex(Publication publication);

    List<BibtexEntry> resolveBibtexEntries(List<Publication> publications);

    String buildBibFileContent(List<BibtexEntry> entries);
}