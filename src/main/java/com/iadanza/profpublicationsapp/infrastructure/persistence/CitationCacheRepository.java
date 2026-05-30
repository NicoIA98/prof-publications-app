package com.iadanza.profpublicationsapp.infrastructure.persistence;

import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;
import java.util.Optional;

/**
 * Repository per la cache locale persistente dei dati citazionali
 * associati a una specifica pubblicazione.
 */
public interface CitationCacheRepository {

    Optional<CitationSummary> findCitationSummary(Publication publication);

    List<CitingDocument> findCitingDocuments(Publication publication);

    void saveCitationData(Publication publication, CitationSummary summary, List<CitingDocument> citingDocuments);

}