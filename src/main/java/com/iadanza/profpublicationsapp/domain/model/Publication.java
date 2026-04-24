package com.iadanza.profpublicationsapp.domain.model;

import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;

import java.util.List;

/**
 * Rappresenta una pubblicazione accademica aggregata da una o più sorgenti.
 * Contiene i metadati principali, gli identificativi esterni e lo stato
 * di completezza del record.
 */
public record Publication(
        String title,
        List<String> authors,
        Integer year,
        String venue,
        String doi,
        String abstractText,
        List<ExternalIdentifier> externalIdentifiers,
        CitationSummary citationSummary,
        SourceType primarySource,
        RecordStatus recordStatus,
        String sourceUrl
) {
}