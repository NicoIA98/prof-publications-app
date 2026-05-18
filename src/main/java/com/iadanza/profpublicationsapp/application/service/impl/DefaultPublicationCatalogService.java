package com.iadanza.profpublicationsapp.application.service.impl;

import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.persistence.PublicationCacheRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Implementazione base del catalogo pubblicazioni.
 * Le pubblicazioni sono considerate canoniche se derivate da IRIS.
 *
 * In questa versione la cache è persistita su SQLite.
 *
 * E3.4-fix conservativo:
 * - deduplica finale dopo il mapping IRIS -> Publication;
 * - NON usa più il solo DOI come chiave unica, perché in IRIS possono comparire record diversi con DOI uguali;
 * - deduplica solo quando coincidono DOI normalizzato + titolo normalizzato + anno;
 * - in caso di duplicato conserva il record più ricco di metadati.
 */
public class DefaultPublicationCatalogService implements PublicationCatalogService {

    private final IrisConnector irisConnector;
    private final PublicationCacheRepository publicationCacheRepository;

    public DefaultPublicationCatalogService(
            IrisConnector irisConnector,
            PublicationCacheRepository publicationCacheRepository
    ) {
        this.irisConnector = irisConnector;
        this.publicationCacheRepository = publicationCacheRepository;
    }

    @Override
    public List<Publication> getCachedPublications(Professor professor) {
        List<Publication> cached = publicationCacheRepository.findCachedPublications(professor);
        return deduplicatePublicationsConservatively(cached, "cache");
    }

    @Override
    public List<Publication> refreshPublicationsFromIris(Professor professor) {
        List<Publication> refreshed = irisConnector.fetchProfessorPublications(professor);
        List<Publication> deduplicated = deduplicatePublicationsConservatively(refreshed, "refresh");

        publicationCacheRepository.savePublications(professor, deduplicated);
        return deduplicated;
    }

    private List<Publication> deduplicatePublicationsConservatively(
            List<Publication> publications,
            String context
    ) {
        if (publications == null || publications.isEmpty()) {
            return List.of();
        }

        Map<String, Publication> ordered = new LinkedHashMap<>();

        for (Publication publication : publications) {
            if (publication == null) {
                continue;
            }

            String key = buildConservativeDeduplicationKey(publication);
            Publication existing = ordered.get(key);

            if (existing == null) {
                ordered.put(key, publication);
                continue;
            }

            Publication better = chooseRicherPublication(existing, publication);

            ordered.put(key, better);

            System.out.println("Publication duplicate detected. context="
                    + context
                    + ", key="
                    + key
                    + ", existingTitle="
                    + safeTitle(existing)
                    + ", duplicateTitle="
                    + safeTitle(publication)
                    + ", keptTitle="
                    + safeTitle(better)
                    + ", keptDoi="
                    + safeValue(better.doi()));
        }

        List<Publication> result = new ArrayList<>(ordered.values());

        if (result.size() != publications.size()) {
            System.out.println("Publication deduplication completed. context="
                    + context
                    + ", before="
                    + publications.size()
                    + ", after="
                    + result.size()
                    + ", removed="
                    + (publications.size() - result.size()));
        }

        return result;
    }

    private String buildConservativeDeduplicationKey(Publication publication) {
        String normalizedDoi = normalizeDoi(publication.doi());
        String normalizedTitle = normalizeText(publication.title());
        String year = publication.year() != null ? publication.year().toString() : "no-year";

        /*
         * Deduplica DOI conservativa:
         * DOI da solo è troppo aggressivo per IRIS.
         * Usiamo DOI + titolo + anno.
         */
        if (!normalizedDoi.isBlank() && !normalizedTitle.isBlank()) {
            return "doi-title-year:"
                    + normalizedDoi
                    + "|"
                    + normalizedTitle
                    + "|"
                    + year;
        }

        /*
         * Fallback per record senza DOI:
         * titolo + anno + primo autore.
         */
        String firstAuthor = normalizeText(firstAuthor(publication));

        if (!normalizedTitle.isBlank()) {
            return "title-year-author:"
                    + normalizedTitle
                    + "|"
                    + year
                    + "|"
                    + firstAuthor;
        }

        /*
         * Ultimo fallback: source URL.
         * Serve solo per non collassare record completamente privi di metadati.
         */
        String sourceUrl = normalizeText(publication.sourceUrl());

        if (!sourceUrl.isBlank()) {
            return "source-url:" + sourceUrl;
        }

        return "object:" + System.identityHashCode(publication);
    }

    private Publication chooseRicherPublication(Publication first, Publication second) {
        int firstScore = metadataCompletenessScore(first);
        int secondScore = metadataCompletenessScore(second);

        if (secondScore > firstScore) {
            return second;
        }

        return first;
    }

    private int metadataCompletenessScore(Publication publication) {
        if (publication == null) {
            return 0;
        }

        int score = 0;

        if (hasText(publication.title())) {
            score += 20;
        }

        if (publication.authors() != null && !publication.authors().isEmpty()) {
            score += 10;
            score += Math.min(publication.authors().size(), 20);
        }

        if (publication.year() != null) {
            score += 10;
        }

        if (hasText(publication.venue())) {
            score += 20;
        }

        if (hasText(publication.doi())) {
            score += 40;
        }

        if (hasText(publication.abstractText())) {
            score += 60;
            score += Math.min(publication.abstractText().length() / 100, 20);
        }

        if (publication.externalIdentifiers() != null && !publication.externalIdentifiers().isEmpty()) {
            score += 10;
        }

        if (hasText(publication.sourceUrl())) {
            score += 10;
        }

        return score;
    }

    private String normalizeDoi(String doi) {
        if (doi == null) {
            return "";
        }

        String normalized = doi.trim().toLowerCase(Locale.ROOT);

        if (normalized.startsWith("https://doi.org/")) {
            normalized = normalized.substring("https://doi.org/".length());
        }

        if (normalized.startsWith("http://doi.org/")) {
            normalized = normalized.substring("http://doi.org/".length());
        }

        if (normalized.startsWith("doi:")) {
            normalized = normalized.substring("doi:".length());
        }

        return normalized.trim();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9à-öø-ÿ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String firstAuthor(Publication publication) {
        if (publication.authors() == null || publication.authors().isEmpty()) {
            return "";
        }

        String firstAuthor = publication.authors().get(0);
        return firstAuthor != null ? firstAuthor : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private String safeTitle(Publication publication) {
        if (publication == null || publication.title() == null || publication.title().isBlank()) {
            return "N/D";
        }

        String title = publication.title().replace("\n", " ").replace("\r", " ").trim();

        if (title.length() <= 80) {
            return title;
        }

        return title.substring(0, 77) + "...";
    }

    private String safeValue(String value) {
        return value != null && !value.isBlank() ? value : "N/D";
    }
}