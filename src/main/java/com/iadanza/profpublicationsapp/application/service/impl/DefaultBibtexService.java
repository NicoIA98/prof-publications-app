package com.iadanza.profpublicationsapp.application.service.impl;

import com.iadanza.profpublicationsapp.application.service.BibtexService;
import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScholarConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScopusConnector;

import java.util.List;
import java.util.Optional;

/**
 * Implementazione base del servizio BibTeX.
 * Strategia:
 * 1. prova IRIS
 * 2. prova Scopus
 * 3. prova Scholar
 * 4. genera fallback interno
 */
public class DefaultBibtexService implements BibtexService {

    private final IrisConnector irisConnector;
    private final ScopusConnector scopusConnector;
    private final ScholarConnector scholarConnector;

    public DefaultBibtexService(
            IrisConnector irisConnector,
            ScopusConnector scopusConnector,
            ScholarConnector scholarConnector
    ) {
        this.irisConnector = irisConnector;
        this.scopusConnector = scopusConnector;
        this.scholarConnector = scholarConnector;
    }

    @Override
    public Optional<BibtexEntry> resolveBibtex(Publication publication) {
        Optional<BibtexEntry> fromIris = irisConnector.fetchBibtexEntry(publication);
        if (fromIris.isPresent()) {
            return fromIris;
        }

        Optional<BibtexEntry> fromScopus = scopusConnector.fetchBibtexEntry(publication);
        if (fromScopus.isPresent()) {
            return fromScopus;
        }

        Optional<BibtexEntry> fromScholar = scholarConnector.fetchBibtexEntry(publication);
        if (fromScholar.isPresent()) {
            return fromScholar;
        }

        return generateFallbackBibtex(publication);
    }

    @Override
    public List<BibtexEntry> resolveBibtexEntries(List<Publication> publications) {
        return publications.stream()
                .map(this::resolveBibtex)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public String buildBibFileContent(List<BibtexEntry> entries) {
        return entries.stream()
                .map(BibtexEntry::rawBibtex)
                .reduce((first, second) -> first + System.lineSeparator() + System.lineSeparator() + second)
                .orElse("");
    }

    private Optional<BibtexEntry> generateFallbackBibtex(Publication publication) {
        if (publication.title() == null || publication.title().isBlank()) {
            return Optional.empty();
        }

        String citationKey = buildCitationKey(publication);
        String entryType = determineEntryType(publication);

        StringBuilder builder = new StringBuilder();
        builder.append("@").append(entryType).append("{").append(citationKey).append(",\n");
        builder.append("  title={").append(escapeBibtex(publication.title())).append("},\n");

        if (publication.authors() != null && !publication.authors().isEmpty()) {
            builder.append("  author={")
                    .append(escapeBibtex(String.join(" and ", publication.authors())))
                    .append("},\n");
        }

        if (publication.venue() != null && !publication.venue().isBlank()) {
            String venueField = "article".equals(entryType) ? "journal" : "booktitle";
            builder.append("  ").append(venueField).append("={")
                    .append(escapeBibtex(publication.venue()))
                    .append("},\n");
        }

        if (publication.year() != null) {
            builder.append("  year={").append(publication.year()).append("},\n");
        }

        if (publication.doi() != null && !publication.doi().isBlank()) {
            builder.append("  doi={").append(escapeBibtex(publication.doi())).append("},\n");
        }

        builder.append("}");

        return Optional.of(new BibtexEntry(
                citationKey,
                entryType,
                builder.toString(),
                SourceType.MANUAL,
                RecordStatus.PARTIAL_DATA
        ));
    }

    private String buildCitationKey(Publication publication) {
        String firstAuthor = (publication.authors() != null && !publication.authors().isEmpty())
                ? publication.authors().get(0)
                : "unknown";

        String normalizedAuthor = firstAuthor.toLowerCase()
                .replaceAll("[^a-z0-9]", "");

        String year = publication.year() != null ? String.valueOf(publication.year()) : "nodate";

        String firstWord = publication.title().toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .trim();

        if (firstWord.isBlank()) {
            firstWord = "publication";
        } else {
            firstWord = firstWord.split("\\s+")[0];
        }

        return normalizedAuthor + year + firstWord;
    }

    private String determineEntryType(Publication publication) {
        String venue = publication.venue() != null ? publication.venue().toLowerCase() : "";

        if (venue.contains("proceedings") || venue.contains("conference")) {
            return "inproceedings";
        }

        if (publication.doi() != null && !publication.doi().isBlank()) {
            return "article";
        }

        return "misc";
    }

    private String escapeBibtex(String value) {
        return value.replace("{", "\\{").replace("}", "\\}");
    }
}