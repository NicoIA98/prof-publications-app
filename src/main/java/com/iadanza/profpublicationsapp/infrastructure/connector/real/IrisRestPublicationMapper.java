package com.iadanza.profpublicationsapp.infrastructure.connector.real;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.ExternalIdentifier;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.dto.IrisItemDto;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.dto.IrisMetadataValueDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mapper da item REST IRIS a Publication di dominio.
 */
public class IrisRestPublicationMapper {

    private final String baseUrl;

    public IrisRestPublicationMapper(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    public Publication toDomainPublication(IrisItemDto item) {
        String title = firstNonBlank(
                item.name(),
                findFirstMetadataValue(item.metadata(), "dc.title"),
                findFirstMetadataContaining(item.metadata(), "title")
        );

        List<String> authors = extractAuthors(item.metadata());
        Integer year = extractYear(item.metadata());
        String venue = firstNonBlank(
                findFirstMetadataValue(item.metadata(), "dc.relation.ispartof"),
                findFirstMetadataContaining(item.metadata(), "journal"),
                findFirstMetadataContaining(item.metadata(), "conference"),
                findFirstMetadataContaining(item.metadata(), "ispartof"),
                findFirstMetadataContaining(item.metadata(), "source")
        );

        String doi = firstNonBlank(
                findFirstMetadataValue(item.metadata(), "dc.identifier.doi"),
                findFirstMetadataContaining(item.metadata(), "doi")
        );

        String abstractText = firstNonBlank(
                findFirstMetadataValue(item.metadata(), "dc.description.abstract"),
                findFirstMetadataContaining(item.metadata(), "abstract")
        );

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
        List<String> authors = new ArrayList<>();

        addValuesIfKeyExists(metadata, authors, "dc.contributor.author");
        addValuesIfKeyExists(metadata, authors, "dc.creator");

        if (authors.isEmpty() && metadata != null) {
            for (Map.Entry<String, List<IrisMetadataValueDto>> entry : metadata.entrySet()) {
                String key = entry.getKey().toLowerCase();
                if (key.contains("author") || key.contains("creator")) {
                    for (IrisMetadataValueDto dto : entry.getValue()) {
                        if (dto != null && dto.value() != null && !dto.value().isBlank()) {
                            authors.add(dto.value());
                        }
                    }
                }
            }
        }

        return authors.stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private Integer extractYear(Map<String, List<IrisMetadataValueDto>> metadata) {
        String raw = firstNonBlank(
                findFirstMetadataValue(metadata, "dc.date.issued"),
                findFirstMetadataContaining(metadata, "date.issued"),
                findFirstMetadataContaining(metadata, "year"),
                findFirstMetadataContaining(metadata, "date")
        );

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String digits = raw.replaceAll("[^0-9]", " ").trim();
        if (digits.isBlank()) {
            return null;
        }

        for (String token : digits.split("\\s+")) {
            if (token.length() == 4) {
                try {
                    return Integer.parseInt(token);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return null;
    }

    private void addValuesIfKeyExists(
            Map<String, List<IrisMetadataValueDto>> metadata,
            List<String> target,
            String key
    ) {
        if (metadata == null || !metadata.containsKey(key)) {
            return;
        }

        for (IrisMetadataValueDto dto : metadata.get(key)) {
            if (dto != null && dto.value() != null && !dto.value().isBlank()) {
                target.add(dto.value());
            }
        }
    }

    private String findFirstMetadataValue(Map<String, List<IrisMetadataValueDto>> metadata, String key) {
        if (metadata == null || key == null || !metadata.containsKey(key)) {
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

    private String findFirstMetadataContaining(Map<String, List<IrisMetadataValueDto>> metadata, String keyword) {
        if (metadata == null || keyword == null) {
            return null;
        }

        String lowerKeyword = keyword.toLowerCase();

        for (Map.Entry<String, List<IrisMetadataValueDto>> entry : metadata.entrySet()) {
            if (!entry.getKey().toLowerCase().contains(lowerKeyword)) {
                continue;
            }

            for (IrisMetadataValueDto dto : entry.getValue()) {
                if (dto != null && dto.value() != null && !dto.value().isBlank()) {
                    return dto.value();
                }
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
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}