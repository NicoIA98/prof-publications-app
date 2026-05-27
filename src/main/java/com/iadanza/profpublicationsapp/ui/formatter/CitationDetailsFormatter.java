package com.iadanza.profpublicationsapp.ui.formatter;

import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;

import java.util.List;

/**
 * Formatter testuale per il pannello "Numero Citazioni e Documenti Citanti".
 *
 * Responsabilità:
 * - trasformare CitationSummary + documenti citanti in testo leggibile;
 * - mostrare chiaramente COMPLETE/PARTIAL_DATA quando Scopus non espone i documenti citanti;
 * - mantenere fuori da ProfessorPublicationsApp la costruzione manuale della stringa.
 */
public final class CitationDetailsFormatter {

    private CitationDetailsFormatter() {
    }

    public static String format(CitationSummary summary, List<CitingDocument> citingDocuments) {
        List<CitingDocument> safeDocuments = citingDocuments != null ? citingDocuments : List.of();

        long scholarDocumentsCount = safeDocuments.stream()
                .filter(document -> document.sourceType() == SourceType.SCHOLAR)
                .count();

        long scopusDocumentsCount = safeDocuments.stream()
                .filter(document -> document.sourceType() == SourceType.SCOPUS)
                .count();

        StringBuilder builder = new StringBuilder();

        builder.append("Citazioni Scopus: ")
                .append(summary != null && summary.scopusCitationCount() != null
                        ? summary.scopusCitationCount()
                        : "N/D")
                .append("\n");

        builder.append("EID Scopus: ")
                .append(summary != null && hasText(summary.scopusEid())
                        ? summary.scopusEid()
                        : "N/D")
                .append("\n");

        builder.append("Citazioni Scholar: ")
                .append(summary != null && summary.scholarCitationCount() != null
                        ? summary.scholarCitationCount()
                        : "N/D")
                .append("\n");

        builder.append("Totale aggregato: ")
                .append(summary != null && summary.totalCitationCount() != null
                        ? summary.totalCitationCount()
                        : "N/D")
                .append("\n\n");

        builder.append("Documenti citanti Scholar in cache: ")
                .append(scholarDocumentsCount)
                .append("\n");

        builder.append("Documenti citanti Scopus in cache: ")
                .append(scopusDocumentsCount)
                .append("\n\n");

        if (summary != null && hasText(summary.scopusCitingDocumentsNote())) {
            builder.append("Stato documenti citanti Scopus: PARTIAL_DATA\n");
            builder.append("Nota Scopus: ")
                    .append(summary.scopusCitingDocumentsNote())
                    .append("\n\n");
        }

        builder.append("Usa i pulsanti \"Documenti citanti Scholar\" e \"Documenti citanti Scopus\" ")
                .append("per aprire l'elenco tabellare dei documenti citanti della pubblicazione selezionata.");

        return builder.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}