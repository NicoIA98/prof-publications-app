package com.iadanza.profpublicationsapp.infrastructure.persistence;

import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;

/**
 * Repository per la cache locale persistente delle pubblicazioni del professore.
 */
public interface PublicationCacheRepository {

    List<Publication> findCachedPublications(Professor professor);

    void savePublications(Professor professor, List<Publication> publications);

}