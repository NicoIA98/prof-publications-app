package com.iadanza.profpublicationsapp.infrastructure.connector.real;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.ExternalIdentifier;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.dto.IrisItemDto;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.dto.IrisMetadataValueDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mapper da item REST IRIS a Publication di dominio.
 *
 * Versione A4-quater:
 * - Venue e DOI restano conservativi
 * - gli autori vengono filtrati in modo più severo
 * - vengono tenuti solo token che assomigliano davvero a nomi di persona
 */
public class IrisRestPublicationMapper {

    private static final List<String> TITLE_KEYS = List.of(
            "dc.title",
            "dc.title.alternative"
    );

    private static final List<String> AUTHOR_KEYS = List.of(
            "dc.contributor.author",
            "dc.creator",
            "dc.description.author",
            "dc.description.authors",
            "dc.description.allauthors",
            "dc.contributor.authors"
    );

    private static final List<String> YEAR_KEYS = List.of(
            "dc.date.issued",
            "dc.date.accessioned"
    );

    private static final List<String> VENUE_KEYS = List.of(
            "dc.relation.ispartof",
            "dc.relation.ispartofseries",
            "dc.source"
    );

    private static final List<String> DOI_KEYS = List.of(
            "dc.identifier.doi"
    );

    private static final List<String> ABSTRACT_KEYS = List.of(
            "dc.description.abstract"
    );

    private final String baseUrl;

    public IrisRestPublicationMapper(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    public Publication toDomainPublication(IrisItemDto item) {
        String title = firstNonBlank(
                item.name(),
                firstMetadataValue(item.metadata(), TITLE_KEYS)
        );

        List<String> authors = extractAuthors(item.metadata());
        Integer year = extractYear(item.metadata());
        String venue = sanitizeVenue(firstMetadataValue(item.metadata(), VENUE_KEYS));
        String doi = sanitizeDoi(firstMetadataValue(item.metadata(), DOI_KEYS));
        String abstractText = sanitizeAbstract(firstMetadataValue(item.metadata(), ABSTRACT_KEYS));

        List<ExternalIdentifier> externalIdentifiers = new ArrayList<>();

        if (item.handle() != null && !item.handle().isBlank()) {
            externalIdentifiers.add(new ExternalIdentifier(
                    IdentifierType.IRIS_ID,
                    item.handle(),
                    SourceType.IRIS
            ));
        }

        String sourceUrl = buildHandleUrl(item.handle());

        return new Publication(
                title != null ? title : "Titolo non disponibile",
                authors,
                year,
                venue,
                doi,
                abstractText,
                externalIdentifiers,
                null,
                SourceType.IRIS,
                RecordStatus.COMPLETE,
                sourceUrl
        );
    }

    private List<String> extractAuthors(Map<String, List<IrisMetadataValueDto>> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> authors = new LinkedHashSet<>();

        // 1. Chiavi autore note
        for (String key : AUTHOR_KEYS) {
            addAuthorsFromMetadataKey(authors, metadata, key);
        }

        // 2. Fallback controllato solo se ancora vuoto
        if (authors.isEmpty()) {
            for (Map.Entry<String, List<IrisMetadataValueDto>> entry : metadata.entrySet()) {
                String key = entry.getKey() != null ? entry.getKey().toLowerCase(Locale.ROOT) : "";
                if (isLikelyAuthorKey(key)) {
                    addAuthorsFromValues(authors, entry.getValue());
                }
            }
        }

        return new ArrayList<>(authors);
    }

    private void addAuthorsFromMetadataKey(
            LinkedHashSet<String> authors,
            Map<String, List<IrisMetadataValueDto>> metadata,
            String key
    ) {
        addAuthorsFromValues(authors, metadata.get(key));
    }

    private void addAuthorsFromValues(
            LinkedHashSet<String> authors,
            List<IrisMetadataValueDto> values
    ) {
        if (values == null || values.isEmpty()) {
            return;
        }

        for (IrisMetadataValueDto dto : values) {
            if (dto == null || dto.value() == null || dto.value().isBlank()) {
                continue;
            }

            for (String candidate : splitPossibleAuthors(dto.value())) {
                String cleaned = sanitizeAuthor(candidate);
                if (cleaned != null && !cleaned.isBlank()) {
                    authors.add(cleaned);
                }
            }
        }
    }

    private boolean isLikelyAuthorKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        boolean positive =
                key.contains("author")
                        || key.contains("creator")
                        || key.contains("allauthors");

        boolean negative =
                key.contains("orcid")
                        || key.contains("advisor")
                        || key.contains("editor")
                        || key.contains("reviewer")
                        || key.contains("sponsor");

        return positive && !negative;
    }

    private List<String> splitPossibleAuthors(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        String normalized = raw.trim().replaceAll("\\s+", " ");

        if (normalized.contains(";")) {
            return toTrimmedList(normalized.split("\\s*;\\s*"));
        }

        if (normalized.contains("|")) {
            return toTrimmedList(normalized.split("\\s*\\|\\s*"));
        }

        long commaCount = normalized.chars().filter(ch -> ch == ',').count();
        if (commaCount >= 2) {
            return toTrimmedList(normalized.split("\\s*,\\s*"));
        }

        return List.of(normalized);
    }

    private List<String> toTrimmedList(String[] parts) {
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part != null) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    private String sanitizeAuthor(String raw) {
        if (raw == null) {
            return null;
        }

        String value = raw.trim().replaceAll("\\s+", " ");

        if (value.isBlank()) {
            return null;
        }

        // rimuove code/sporcizia tipica IRIS
        value = value.replaceAll("(?i)lecture notes in computer science###\\S+", "").trim();
        value = value.replaceAll("(?i)lecture notes on computer science###\\S+", "").trim();
        value = value.replaceAll("###\\S+", "").trim();

        // rimuove venue note se finite nel campo autore
        value = value.replaceAll("(?i)^lecture notes in computer science$", "").trim();
        value = value.replaceAll("(?i)^lecture notes on computer science.*$", "").trim();
        value = value.replaceAll("(?i)^proceedings of.*$", "").trim();
        value = value.replaceAll("(?i)^journal of.*$", "").trim();
        value = value.replaceAll("(?i)^sensors$", "").trim();
        value = value.replaceAll("(?i)^engineering applications.*$", "").trim();

        if (value.isBlank()) {
            return null;
        }

        String lower = value.toLowerCase(Locale.ROOT);

        if (lower.equals("false") || lower.equals("true") || lower.equals("orcid")) {
            return null;
        }

        if (value.startsWith("http://") || value.startsWith("https://")) {
            return null;
        }

        if (value.startsWith("10.")) {
            return null;
        }

        if (value.matches("^[0-9.,\\- ]+$")) {
            return null;
        }

        if (value.length() < 3) {
            return null;
        }

        if (containsVenueKeyword(lower)) {
            return null;
        }

        if (!looksLikePersonName(value)) {
            return null;
        }

        return value;
    }

    private boolean containsVenueKeyword(String lower) {
        return lower.contains("lecture notes")
                || lower.contains("computer science")
                || lower.contains("proceedings")
                || lower.contains("journal")
                || lower.contains("transactions")
                || lower.contains("conference")
                || lower.contains("spectrometer")
                || lower.contains("engineering")
                || lower.contains("sensors");
    }

    private boolean looksLikePersonName(String value) {
        String cleaned = value.replaceAll("\\.", "").trim();
        String[] parts = cleaned.split("\\s+");

        if (parts.length < 2 || parts.length > 5) {
            return false;
        }

        int acceptableTokens = 0;

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            if (part.matches("(?i)de|di|del|della|der|van|von|da|dos|du")) {
                acceptableTokens++;
                continue;
            }

            if (part.matches("[A-ZÀ-ÖØ-Ý][a-zà-öø-ÿ'’\\-]+")) {
                acceptableTokens++;
                continue;
            }

            if (part.matches("[A-ZÀ-ÖØ-Ý]{2,}")) {
                acceptableTokens++;
                continue;
            }

            if (part.matches("[A-ZÀ-ÖØ-Ý]")) {
                acceptableTokens++;
                continue;
            }

            return false;
        }

        return acceptableTokens == parts.length;
    }

    private Integer extractYear(Map<String, List<IrisMetadataValueDto>> metadata) {
        String raw = firstMetadataValue(metadata, YEAR_KEYS);

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String[] tokens = raw.split("[^0-9]");
        for (String token : tokens) {
            if (token.length() == 4) {
                try {
                    int year = Integer.parseInt(token);
                    if (year >= 1900 && year <= 2100) {
                        return year;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return null;
    }

    private String sanitizeVenue(String raw) {
        if (raw == null) {
            return null;
        }

        String value = raw.trim().replaceAll("\\s+", " ");

        if (value.isBlank()) {
            return null;
        }

        String lower = value.toLowerCase(Locale.ROOT);

        if (lower.equals("false") || lower.equals("true") || lower.equals("orcid")) {
            return null;
        }

        if (value.matches("^[0-9.]+$")) {
            return null;
        }

        if (value.matches("^[0-9]{6,}$")) {
            return null;
        }

        return value;
    }

    private String sanitizeDoi(String raw) {
        if (raw == null) {
            return null;
        }

        String value = raw.trim();

        if (value.isBlank()) {
            return null;
        }

        value = value.replace("https://doi.org/", "")
                .replace("http://doi.org/", "")
                .trim();

        if (value.startsWith("10.")) {
            return value;
        }

        return null;
    }

    private String sanitizeAbstract(String raw) {
        if (raw == null) {
            return null;
        }

        String value = raw.trim().replaceAll("\\s+", " ");

        if (value.isBlank()) {
            return null;
        }

        return value;
    }

    private String firstMetadataValue(Map<String, List<IrisMetadataValueDto>> metadata, List<String> keys) {
        if (metadata == null || keys == null) {
            return null;
        }

        for (String key : keys) {
            String value = firstMetadataValue(metadata, key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private String firstMetadataValue(Map<String, List<IrisMetadataValueDto>> metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }

        List<IrisMetadataValueDto> values = metadata.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }

        for (IrisMetadataValueDto dto : values) {
            if (dto != null && dto.value() != null && !dto.value().isBlank()) {
                return dto.value();
            }
        }

        return null;
    }

    private String buildHandleUrl(String handle) {
        if (handle == null || handle.isBlank()) {
            return null;
        }

        if (handle.startsWith("http://") || handle.startsWith("https://")) {
            return handle;
        }

        return baseUrl + "/handle/" + handle;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }

        return value;
    }
}