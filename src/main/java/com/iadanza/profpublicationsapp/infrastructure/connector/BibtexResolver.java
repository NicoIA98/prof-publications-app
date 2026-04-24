package com.iadanza.profpublicationsapp.infrastructure.connector;

import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.Optional;

/**
 * Contratto infrastrutturale per il recupero o la generazione
 * di una entry BibTeX a partire da una pubblicazione.
 */
public interface BibtexResolver {

    Optional<BibtexEntry> resolve(Publication publication);
}