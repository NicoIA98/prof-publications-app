package com.iadanza.profpublicationsapp.domain.model;

import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;

import java.util.List;

/**
 * Rappresenta un documento che cita una pubblicazione del professore.
 * I dati possono provenire da Scopus o da Scholar tramite SerpApi.
 */
public record CitingDocument(
        String title,
        List<String> authors,
        Integer year,
        String doi,
        SourceType sourceType,
        RecordStatus recordStatus,
        String sourceUrl
) {
}