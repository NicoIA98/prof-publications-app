package com.iadanza.profpublicationsapp.domain.model;

/**
 * Riepiloga il numero di citazioni di una pubblicazione
 * provenienti da diverse sorgenti bibliometriche.
 */
public record CitationSummary(
        Integer scopusCitationCount,
        Integer scholarCitationCount,
        Integer totalCitationCount
) {
}