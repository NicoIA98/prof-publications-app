package com.iadanza.profpublicationsapp.infrastructure.connector;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.util.List;
import java.util.Optional;

/**
 * Connettore ibrido:
 * - mantiene il fake per tutto quello che già funziona
 * - usa il real per IRIS ID reali tipo rp00418
 */
public class HybridIrisConnector implements IrisConnector {

    private final IrisConnector fakeConnector;
    private final IrisConnector realConnector;

    public HybridIrisConnector(IrisConnector fakeConnector, IrisConnector realConnector) {
        this.fakeConnector = fakeConnector;
        this.realConnector = realConnector;
    }

    @Override
    public List<Professor> searchProfessors(String query) {
        return fakeConnector.searchProfessors(query);
    }

    @Override
    public Optional<Professor> findProfessorByIdentifier(IdentifierType identifierType, String value) {
        if (identifierType == IdentifierType.IRIS_ID && value != null && value.startsWith("rp")) {
            Optional<Professor> real = realConnector.findProfessorByIdentifier(identifierType, value);
            if (real.isPresent()) {
                return real;
            }
        }

        return fakeConnector.findProfessorByIdentifier(identifierType, value);
    }

    @Override
    public List<Publication> fetchProfessorPublications(Professor professor) {
        boolean hasRealIrisId = professor.externalIdentifiers().stream()
                .anyMatch(identifier ->
                        identifier.type() == IdentifierType.IRIS_ID
                                && identifier.value() != null
                                && identifier.value().startsWith("rp")
                );

        if (hasRealIrisId) {
            List<Publication> realPublications = realConnector.fetchProfessorPublications(professor);
            if (!realPublications.isEmpty()) {
                return realPublications;
            }
        }

        return fakeConnector.fetchProfessorPublications(professor);
    }

    @Override
    public Optional<BibtexEntry> fetchBibtexEntry(Publication publication) {
        Optional<BibtexEntry> real = realConnector.fetchBibtexEntry(publication);
        if (real.isPresent()) {
            return real;
        }

        return fakeConnector.fetchBibtexEntry(publication);
    }
}