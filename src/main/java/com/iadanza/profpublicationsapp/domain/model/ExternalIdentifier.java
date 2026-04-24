package com.iadanza.profpublicationsapp.domain.model;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;

/**
 * Rappresenta un identificativo esterno associato a un'entità di dominio,
 * ad esempio ORCID, DOI, Scopus Author ID o Scholar Author ID.
 */
public record ExternalIdentifier(
        IdentifierType type,
        String value,
        SourceType sourceType
) {
}