package com.iadanza.profpublicationsapp.application.service.impl;

import com.iadanza.profpublicationsapp.application.service.CitationService;
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
 * Implementazione del servizio citazionale.
 *
 * Responsabilità:
 * - leggere citation summary e documenti citanti dalla cache SQLite;
 * - aggiornare il citation summary interrogando Scopus e Scholar;
 * - aggiornare i documenti citanti interrogando Scopus e Scholar;
 * - fondere i risultati delle sorgenti evitando duplicati;
 * - proteggere la cache da refresh peggiorativi/parziali delle sorgenti esterne.
 *
 * Nota:
 * Scopus è la sorgente bibliografica/citazionale ufficiale.
 * Scholar tramite SerpApi è una sorgente secondaria e può restituire dati parziali o variabili.
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
        List<CitingDocument> existingDocuments = getCachedCitingDocuments(publication);

        List<CitingDocument> scopusDocs = scopusConnector.findCitingDocuments(publication);
        List<CitingDocument> scholarDocs = scholarConnector.findCitingDocuments(publication);

        List<CitingDocument> refreshedDocuments = mergeAndDeduplicateDocuments(scopusDocs, scholarDocs);

        /*
         * Importante:
         * non sostituiamo più la cache con il solo risultato dell'ultimo refresh.
         * Scholar/SerpApi può restituire meno documenti in alcuni momenti.
         * Manteniamo quindi i documenti già raccolti e aggiungiamo quelli nuovi.
         */
        List<CitingDocument> mergedWithCache = mergeAndDeduplicateDocuments(
                existingDocuments,
                refreshedDocuments
        );

        CitationSummary existingSummary = getCachedCitationSummary(publication);
        citationCacheRepository.saveCitationData(publication, existingSummary, mergedWithCache);

        return mergedWithCache;
    }

    private Integer computeTotal(Integer scopusCount, Integer scholarCount) {
        if (scopusCount == null && scholarCount == null) {
            return null;
        }

        return (scopusCount != null ? scopusCount : 0)
                + (scholarCount != null ? scholarCount : 0);
    }

    private List<CitingDocument> mergeAndDeduplicateDocuments(
            List<CitingDocument> first,
            List<CitingDocument> second
    ) {
        Map<String, CitingDocument> merged = new LinkedHashMap<>();

        addDocumentsToMap(merged, first);
        addDocumentsToMap(merged, second);

        return new ArrayList<>(merged.values());
    }

    private void addDocumentsToMap(
            Map<String, CitingDocument> target,
            List<CitingDocument> documents
    ) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        for (CitingDocument document : documents) {
            if (document == null) {
                continue;
            }

            target.put(buildCitingDocumentKey(document), document);
        }
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