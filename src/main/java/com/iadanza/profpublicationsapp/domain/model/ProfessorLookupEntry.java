package com.iadanza.profpublicationsapp.domain.model;

import java.util.Locale;

/**
 * Riga della rubrica locale per ricerca professori tramite Codice Fiscale.
 */
public record ProfessorLookupEntry(
        String nome,
        String cognome,
        String codiceFiscale
) {

    public ProfessorLookupEntry {
        nome = clean(nome);
        cognome = clean(cognome);
        codiceFiscale = clean(codiceFiscale).toUpperCase(Locale.ROOT);
    }

    public boolean matches(String query) {
        String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);

        if (normalizedQuery.isBlank()) {
            return true;
        }

        return nome.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || cognome.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || codiceFiscale.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || (nome + " " + cognome).toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || (cognome + " " + nome).toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    public boolean hasSameFiscalCode(ProfessorLookupEntry other) {
        if (other == null) {
            return false;
        }

        return codiceFiscale.equalsIgnoreCase(other.codiceFiscale());
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }

        return value.trim().replaceAll("\\s+", " ");
    }
}