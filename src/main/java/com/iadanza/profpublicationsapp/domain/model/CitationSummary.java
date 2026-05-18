package com.iadanza.profpublicationsapp.domain.model;

/**
 * Riepiloga il numero di citazioni di una pubblicazione
 * provenienti da diverse sorgenti bibliometriche.
 *
 * #229-B:
 * - aggiunto scopusEid per mostrare l'identificativo Scopus del record;
 * - aggiunta nota opzionale sui documenti citanti Scopus, utile quando
 *   il citation count è disponibile ma l'elenco dei documenti citanti
 *   non è accessibile con le autorizzazioni API correnti.
 */
public record CitationSummary(
        Integer scopusCitationCount,
        Integer scholarCitationCount,
        Integer totalCitationCount,
        String scopusEid,
        String scopusCitingDocumentsNote
) {

    public CitationSummary(
            Integer scopusCitationCount,
            Integer scholarCitationCount,
            Integer totalCitationCount
    ) {
        this(
                scopusCitationCount,
                scholarCitationCount,
                totalCitationCount,
                null,
                null
        );
    }
}