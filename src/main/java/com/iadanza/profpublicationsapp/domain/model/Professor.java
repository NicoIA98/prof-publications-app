package com.iadanza.profpublicationsapp.domain.model;

import java.util.List;

/**
 * Rappresenta un docente/professore ricercabile nell'applicazione.
 * Contiene i dati anagrafici essenziali e gli identificativi esterni
 * utili per interrogare le varie sorgenti.
 */
public record Professor(
        String firstName,
        String lastName,
        String fullName,
        String affiliation,
        List<ExternalIdentifier> externalIdentifiers
) {
}