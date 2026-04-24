package com.iadanza.profpublicationsapp.domain.model;

import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;

/**
 * Rappresenta una entry BibTeX pronta per essere copiata o salvata su file.
 * Contiene sia il testo completo generato/recuperato sia alcune informazioni di contesto.
 */
public record BibtexEntry(
        String citationKey,
        String entryType,
        String rawBibtex,
        SourceType sourceType,
        RecordStatus recordStatus
) {
}