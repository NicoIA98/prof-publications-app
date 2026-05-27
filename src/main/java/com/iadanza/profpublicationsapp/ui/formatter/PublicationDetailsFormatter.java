package com.iadanza.profpublicationsapp.ui.formatter;

import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;

/**
 * Formatter testuale per il pannello "Dettaglio pubblicazione".
 *
 * Responsabilità:
 * - trasformare una Publication in testo leggibile per la UI;
 * - mantenere fuori da ProfessorPublicationsApp la costruzione manuale della stringa.
 */
public final class PublicationDetailsFormatter {

    private PublicationDetailsFormatter() {
    }

    public static String format(Publication publication) {
        if (publication == null) {
            return "Seleziona una pubblicazione per visualizzarne il dettaglio.";
        }

        String authorsText = formatAuthors(publication.authors());

        StringBuilder builder = new StringBuilder();

        builder.append("Titolo: ")
                .append(safeText(publication.title()))
                .append("\n\n");

        builder.append("Autori: ")
                .append(authorsText)
                .append("\n");

        builder.append("Anno: ")
                .append(publication.year() != null ? publication.year() : "N/D")
                .append("\n");

        builder.append("Venue: ")
                .append(safeText(publication.venue()))
                .append("\n");

        builder.append("DOI: ")
                .append(safeText(publication.doi()))
                .append("\n");

        builder.append("Stato record: ")
                .append(publication.recordStatus() != null ? publication.recordStatus() : "N/D")
                .append("\n");

        builder.append("Sorgente primaria: ")
                .append(publication.primarySource() != null ? publication.primarySource() : "N/D")
                .append("\n");

        builder.append("URL sorgente: ")
                .append(safeText(publication.sourceUrl()))
                .append("\n\n");

        builder.append("Abstract:\n");
        builder.append(safeText(publication.abstractText()));

        return builder.toString();
    }

    private static String formatAuthors(List<String> authors) {
        if (authors == null || authors.isEmpty()) {
            return "N/D";
        }

        return String.join(", ", authors);
    }

    private static String safeText(String value) {
        if (value == null || value.trim().isBlank()) {
            return "N/D";
        }

        return value;
    }
}