package com.iadanza.profpublicationsapp.infrastructure.connector.fake;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.ExternalIdentifier;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.json.IrisExternalIdentifierJson;
import com.iadanza.profpublicationsapp.infrastructure.json.IrisProfessorJson;
import com.iadanza.profpublicationsapp.infrastructure.json.IrisProfessorPublicationsJson;
import com.iadanza.profpublicationsapp.infrastructure.json.IrisPublicationJson;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

/**
 * Implementazione fake di IrisConnector che legge i dati da file JSON
 * presenti nelle resources del progetto.
 */
public class FakeIrisConnector implements IrisConnector {

    private static final String DEFAULT_RESOURCE = "/mock/iris/mario-rossi-publications.json";

    private final ObjectMapper objectMapper;

    public FakeIrisConnector() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<Professor> searchProfessors(String query) {
        Professor professor = loadProfessor();

        if (query == null || query.isBlank()) {
            return List.of(professor);
        }

        String normalizedQuery = query.toLowerCase().trim();

        boolean matches = professor.fullName().toLowerCase().contains(normalizedQuery)
                || professor.firstName().toLowerCase().contains(normalizedQuery)
                || professor.lastName().toLowerCase().contains(normalizedQuery)
                || professor.affiliation().toLowerCase().contains(normalizedQuery);

        return matches ? List.of(professor) : List.of();
    }

    @Override
    public Optional<Professor> findProfessorByIdentifier(IdentifierType identifierType, String value) {
        Professor professor = loadProfessor();

        boolean match = professor.externalIdentifiers().stream()
                .anyMatch(identifier ->
                        identifier.type() == identifierType &&
                                identifier.value().equalsIgnoreCase(value)
                );

        return match ? Optional.of(professor) : Optional.empty();
    }

    @Override
    public List<Publication> fetchProfessorPublications(Professor professor) {
        IrisProfessorPublicationsJson json = loadProfessorData();

        if (!json.professor().fullName().equalsIgnoreCase(professor.fullName())) {
            return List.of();
        }

        return json.publications().stream()
                .map(this::toDomainPublication)
                .toList();
    }

    @Override
    public Optional<BibtexEntry> fetchBibtexEntry(Publication publication) {
        if ("10.1000/jfx-arch-2024".equalsIgnoreCase(publication.doi())) {
            return Optional.of(new BibtexEntry(
                    "rossi2024javafx",
                    "article",
                    """
                    @article{rossi2024javafx,
                      title={A JavaFX Desktop Architecture for Academic Search},
                      author={Rossi, Mario and Verdi, Luca},
                      journal={Journal of Software Architecture},
                      year={2024},
                      doi={10.1000/jfx-arch-2024}
                    }
                    """,
                    SourceType.IRIS,
                    RecordStatus.COMPLETE
            ));
        }

        return Optional.empty();
    }

    private IrisProfessorPublicationsJson loadProfessorData() {
        try (InputStream inputStream = getClass().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Mock IRIS resource not found: " + DEFAULT_RESOURCE);
            }

            IrisProfessorPublicationsJson json = objectMapper.readValue(
                    inputStream,
                    IrisProfessorPublicationsJson.class
            );
            return json;

        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read mock IRIS JSON resource", e);
        }
    }

    private Professor toDomainProfessor(IrisProfessorJson json) {
        return new Professor(
                json.firstName(),
                json.lastName(),
                json.fullName(),
                json.affiliation(),
                json.externalIdentifiers().stream()
                        .map(this::toDomainExternalIdentifier)
                        .toList()
        );
    }

    private Publication toDomainPublication(IrisPublicationJson json) {
        return new Publication(
                json.title(),
                json.authors(),
                json.year(),
                json.venue(),
                json.doi(),
                json.abstractText(),
                json.externalIdentifiers().stream()
                        .map(this::toDomainExternalIdentifier)
                        .toList(),
                null,
                SourceType.IRIS,
                RecordStatus.valueOf(json.recordStatus()),
                json.sourceUrl()
        );
    }

    private ExternalIdentifier toDomainExternalIdentifier(IrisExternalIdentifierJson json) {
        return new ExternalIdentifier(
                IdentifierType.valueOf(json.type()),
                json.value(),
                SourceType.valueOf(json.sourceType())
        );
    }

    private Professor loadProfessor() {
        return toDomainProfessor(loadProfessorData().professor());
    }
}