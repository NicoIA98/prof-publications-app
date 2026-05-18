package com.iadanza.profpublicationsapp.application.service.impl;

import com.iadanza.profpublicationsapp.application.service.CitationService;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScholarConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScopusConnector;
import com.iadanza.profpublicationsapp.infrastructure.persistence.CitationCacheRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementazione base del servizio citazionale.
 * In questa fase usa una cache persistente su SQLite.
 *
 * #229-B:
 * - conserva l'EID Scopus restituito dal RealScopusConnector;
 * - conserva la nota PARTIAL_DATA sui documenti citanti Scopus non disponibili;
 * - mantiene compatibile il merge Scopus + Scholar.
 */
public class DefaultCitationService implements CitationService {

    private final ScopusConnector scopusConnector;
    private final ScholarConnector scholarConnector;
    private final CitationCacheRepository citationCacheRepository;

    public DefaultCitationService(
            ScopusConnector scopusConnector,
            ScholarConnector scholarConnector,
            CitationCacheRepository citationCacheRepository
    ) {
        this.scopusConnector = scopusConnector;
        this.scholarConnector = scholarConnector;
        this.citationCacheRepository = citationCacheRepository;
    }

    @Override
    public CitationSummary getCachedCitationSummary(Publication publication) {
        return citationCacheRepository.findCitationSummary(publication)
                .orElse(new CitationSummary(null, null, null));
    }

    @Override
    public List<CitingDocument> getCachedCitingDocuments(Publication publication) {
        return citationCacheRepository.findCitingDocuments(publication);
    }

    @Override
    public CitationSummary refreshCitationSummary(Publication publication) {
        CitationSummary existing = getCachedCitationSummary(publication);

        CitationSummary scopusSummary = scopusConnector.fetchCitationSummary(publication).orElse(null);
        CitationSummary scholarSummary = scholarConnector.fetchCitationSummary(publication).orElse(null);

        Integer scopusCount = scopusSummary != null
                ? scopusSummary.scopusCitationCount()
                : existing.scopusCitationCount();

        Integer scholarCount = scholarSummary != null
                ? scholarSummary.scholarCitationCount()
                : existing.scholarCitationCount();

        Integer total = computeTotal(scopusCount, scholarCount);

        String scopusEid = scopusSummary != null
                ? scopusSummary.scopusEid()
                : existing.scopusEid();

        String scopusCitingDocumentsNote = scopusSummary != null
                ? scopusSummary.scopusCitingDocumentsNote()
                : existing.scopusCitingDocumentsNote();

        CitationSummary merged = new CitationSummary(
                scopusCount,
                scholarCount,
                total,
                scopusEid,
                scopusCitingDocumentsNote
        );

        List<CitingDocument> existingDocuments = getCachedCitingDocuments(publication);

        citationCacheRepository.saveCitationData(publication, merged, existingDocuments);
        return merged;
    }

    @Override
    public List<CitingDocument> refreshCitingDocuments(Publication publication) {
        List<CitingDocument> scopusDocs = scopusConnector.findCitingDocuments(publication);
        List<CitingDocument> scholarDocs = scholarConnector.findCitingDocuments(publication);

        List<CitingDocument> merged = mergeAndDeduplicateDocuments(scopusDocs, scholarDocs);
        CitationSummary existingSummary = getCachedCitationSummary(publication);

        citationCacheRepository.saveCitationData(publication, existingSummary, merged);
        return merged;
    }

    public CitationSummary refreshScopusCitationSummary(Publication publication) {
        CitationSummary existing = getCachedCitationSummary(publication);
        CitationSummary scopusSummary = scopusConnector.fetchCitationSummary(publication).orElse(null);

        Integer scopusCount = scopusSummary != null
                ? scopusSummary.scopusCitationCount()
                : existing.scopusCitationCount();

        Integer scholarCount = existing.scholarCitationCount();
        Integer total = computeTotal(scopusCount, scholarCount);

        String scopusEid = scopusSummary != null
                ? scopusSummary.scopusEid()
                : existing.scopusEid();

        String scopusCitingDocumentsNote = scopusSummary != null
                ? scopusSummary.scopusCitingDocumentsNote()
                : existing.scopusCitingDocumentsNote();

        CitationSummary updated = new CitationSummary(
                scopusCount,
                scholarCount,
                total,
                scopusEid,
                scopusCitingDocumentsNote
        );

        citationCacheRepository.saveCitationData(publication, updated, getCachedCitingDocuments(publication));
        return updated;
    }

    public CitationSummary refreshScholarCitationSummary(Publication publication) {
        CitationSummary existing = getCachedCitationSummary(publication);
        CitationSummary scholarSummary = scholarConnector.fetchCitationSummary(publication).orElse(null);

        Integer scopusCount = existing.scopusCitationCount();

        Integer scholarCount = scholarSummary != null
                ? scholarSummary.scholarCitationCount()
                : existing.scholarCitationCount();

        Integer total = computeTotal(scopusCount, scholarCount);

        CitationSummary updated = new CitationSummary(
                scopusCount,
                scholarCount,
                total,
                existing.scopusEid(),
                existing.scopusCitingDocumentsNote()
        );

        citationCacheRepository.saveCitationData(publication, updated, getCachedCitingDocuments(publication));
        return updated;
    }

    public List<CitingDocument> refreshScopusCitingDocuments(Publication publication) {
        List<CitingDocument> existing = getCachedCitingDocuments(publication);
        List<CitingDocument> preserved = existing.stream()
                .filter(document -> document.sourceType() != SourceType.SCOPUS)
                .toList();

        List<CitingDocument> refreshed = scopusConnector.findCitingDocuments(publication);
        List<CitingDocument> merged = mergeAndDeduplicateDocuments(preserved, refreshed);

        citationCacheRepository.saveCitationData(publication, getCachedCitationSummary(publication), merged);
        return merged;
    }

    public List<CitingDocument> refreshScholarCitingDocuments(Publication publication) {
        List<CitingDocument> existing = getCachedCitingDocuments(publication);
        List<CitingDocument> preserved = existing.stream()
                .filter(document -> document.sourceType() != SourceType.SCHOLAR)
                .toList();

        List<CitingDocument> refreshed = scholarConnector.findCitingDocuments(publication);
        List<CitingDocument> merged = mergeAndDeduplicateDocuments(preserved, refreshed);

        citationCacheRepository.saveCitationData(publication, getCachedCitationSummary(publication), merged);
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

    private String buildCitingDocumentKey(CitingDocument document) {
        if (document.doi() != null && !document.doi().isBlank()) {
            return "doi:" + document.doi().toLowerCase().trim();
        }

        String title = document.title() != null ? document.title().toLowerCase().trim() : "";
        int year = document.year() != null ? document.year() : 0;

        return document.sourceType() + "|" + title + "|" + year;
    }
}