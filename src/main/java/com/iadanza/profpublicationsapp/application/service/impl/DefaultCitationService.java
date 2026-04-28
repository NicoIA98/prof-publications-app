package com.iadanza.profpublicationsapp.application.service.impl;

import com.iadanza.profpublicationsapp.application.service.CitationService;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScholarConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScopusConnector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementazione base del servizio citazionale.
 * In questa fase usa una cache in memoria e fake connector.
 */
public class DefaultCitationService implements CitationService {

    private final ScopusConnector scopusConnector;
    private final ScholarConnector scholarConnector;

    private final Map<String, CitationSummary> citationSummaryCache = new LinkedHashMap<>();
    private final Map<String, List<CitingDocument>> citingDocumentsCache = new LinkedHashMap<>();

    public DefaultCitationService(ScopusConnector scopusConnector, ScholarConnector scholarConnector) {
        this.scopusConnector = scopusConnector;
        this.scholarConnector = scholarConnector;
    }

    @Override
    public CitationSummary getCachedCitationSummary(Publication publication) {
        return citationSummaryCache.getOrDefault(
                buildPublicationKey(publication),
                new CitationSummary(null, null, null)
        );
    }

    @Override
    public List<CitingDocument> getCachedCitingDocuments(Publication publication) {
        return citingDocumentsCache.getOrDefault(buildPublicationKey(publication), List.of());
    }

    @Override
    public CitationSummary refreshCitationSummary(Publication publication) {
        CitationSummary scopusSummary = scopusConnector.fetchCitationSummary(publication).orElse(null);
        CitationSummary scholarSummary = scholarConnector.fetchCitationSummary(publication).orElse(null);

        Integer scopusCount = scopusSummary != null ? scopusSummary.scopusCitationCount() : null;
        Integer scholarCount = scholarSummary != null ? scholarSummary.scholarCitationCount() : null;

        Integer total = null;
        if (scopusCount != null || scholarCount != null) {
            total = (scopusCount != null ? scopusCount : 0)
                    + (scholarCount != null ? scholarCount : 0);
        }

        CitationSummary merged = new CitationSummary(scopusCount, scholarCount, total);
        citationSummaryCache.put(buildPublicationKey(publication), merged);
        return merged;
    }

    @Override
    public List<CitingDocument> refreshCitingDocuments(Publication publication) {
        List<CitingDocument> scopusDocs = scopusConnector.findCitingDocuments(publication);
        List<CitingDocument> scholarDocs = scholarConnector.findCitingDocuments(publication);

        List<CitingDocument> merged = mergeAndDeduplicateDocuments(scopusDocs, scholarDocs);
        citingDocumentsCache.put(buildPublicationKey(publication), merged);
        return merged;
    }

    public CitationSummary refreshScopusCitationSummary(Publication publication) {
        CitationSummary existing = getCachedCitationSummary(publication);
        CitationSummary scopusSummary = scopusConnector.fetchCitationSummary(publication).orElse(null);

        Integer scopusCount = scopusSummary != null ? scopusSummary.scopusCitationCount() : existing.scopusCitationCount();
        Integer scholarCount = existing.scholarCitationCount();
        Integer total = computeTotal(scopusCount, scholarCount);

        CitationSummary updated = new CitationSummary(scopusCount, scholarCount, total);
        citationSummaryCache.put(buildPublicationKey(publication), updated);
        return updated;
    }

    public CitationSummary refreshScholarCitationSummary(Publication publication) {
        CitationSummary existing = getCachedCitationSummary(publication);
        CitationSummary scholarSummary = scholarConnector.fetchCitationSummary(publication).orElse(null);

        Integer scopusCount = existing.scopusCitationCount();
        Integer scholarCount = scholarSummary != null ? scholarSummary.scholarCitationCount() : existing.scholarCitationCount();
        Integer total = computeTotal(scopusCount, scholarCount);

        CitationSummary updated = new CitationSummary(scopusCount, scholarCount, total);
        citationSummaryCache.put(buildPublicationKey(publication), updated);
        return updated;
    }

    public List<CitingDocument> refreshScopusCitingDocuments(Publication publication) {
        List<CitingDocument> existing = getCachedCitingDocuments(publication);
        List<CitingDocument> preserved = existing.stream()
                .filter(document -> document.sourceType() != SourceType.SCOPUS)
                .toList();

        List<CitingDocument> refreshed = scopusConnector.findCitingDocuments(publication);
        List<CitingDocument> merged = mergeAndDeduplicateDocuments(preserved, refreshed);

        citingDocumentsCache.put(buildPublicationKey(publication), merged);
        return merged;
    }

    public List<CitingDocument> refreshScholarCitingDocuments(Publication publication) {
        List<CitingDocument> existing = getCachedCitingDocuments(publication);
        List<CitingDocument> preserved = existing.stream()
                .filter(document -> document.sourceType() != SourceType.SCHOLAR)
                .toList();

        List<CitingDocument> refreshed = scholarConnector.findCitingDocuments(publication);
        List<CitingDocument> merged = mergeAndDeduplicateDocuments(preserved, refreshed);

        citingDocumentsCache.put(buildPublicationKey(publication), merged);
        return merged;
    }

    private Integer computeTotal(Integer scopusCount, Integer scholarCount) {
        if (scopusCount == null && scholarCount == null) {
            return null;
        }

        return (scopusCount != null ? scopusCount : 0)
                + (scholarCount != null ? scholarCount : 0);
    }

    private List<CitingDocument> mergeAndDeduplicateDocuments(List<CitingDocument> first, List<CitingDocument> second) {
        Map<String, CitingDocument> merged = new LinkedHashMap<>();

        for (CitingDocument document : first) {
            merged.put(buildCitingDocumentKey(document), document);
        }

        for (CitingDocument document : second) {
            merged.put(buildCitingDocumentKey(document), document);
        }

        return new ArrayList<>(merged.values());
    }

    private String buildPublicationKey(Publication publication) {
        if (publication.doi() != null && !publication.doi().isBlank()) {
            return "DOI:" + publication.doi().toLowerCase().trim();
        }

        return "META:"
                + normalize(publication.title())
                + "|"
                + (publication.year() != null ? publication.year() : 0);
    }

    private String buildCitingDocumentKey(CitingDocument document) {
        if (document.doi() != null && !document.doi().isBlank()) {
            return "DOI:" + document.doi().toLowerCase().trim();
        }

        return document.sourceType()
                + "|"
                + normalize(document.title())
                + "|"
                + (document.year() != null ? document.year() : 0);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase().trim();
    }
}