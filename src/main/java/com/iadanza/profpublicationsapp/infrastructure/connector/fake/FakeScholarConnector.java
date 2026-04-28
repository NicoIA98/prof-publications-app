package com.iadanza.profpublicationsapp.infrastructure.connector.fake;

import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.domain.model.ScholarAuthorMapping;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScholarConnector;

import java.util.List;
import java.util.Optional;

/**
 * Implementazione fake di ScholarConnector per simulare
 * dati citazionali, documenti citanti e BibTeX.
 */
public class FakeScholarConnector implements ScholarConnector {

    @Override
    public Optional<ScholarAuthorMapping> findScholarAuthorMapping(Professor professor) {
        if ("Mario Rossi".equalsIgnoreCase(professor.fullName())) {
            return Optional.of(new ScholarAuthorMapping(
                    "Mario Rossi",
                    "University of Verona",
                    "scholar-mario-rossi-001",
                    true
            ));
        }

        return Optional.empty();
    }

    @Override
    public Optional<CitationSummary> fetchCitationSummary(Publication publication) {
        if ("10.1000/jfx-arch-2024".equalsIgnoreCase(publication.doi())) {
            return Optional.of(new CitationSummary(null, 18, 18));
        }

        if ("Institutional Repository Integration in University Systems"
                .equalsIgnoreCase(publication.title())) {
            return Optional.of(new CitationSummary(null, 5, 5));
        }

        return Optional.empty();
    }

    @Override
    public List<CitingDocument> findCitingDocuments(Publication publication) {
        if ("10.1000/jfx-arch-2024".equalsIgnoreCase(publication.doi())) {
            return List.of(
                    new CitingDocument(
                            "Scholarly Desktop Interfaces for Research Analytics",
                            List.of("Laura Fontana"),
                            2025,
                            null,
                            SourceType.SCHOLAR,
                            RecordStatus.PARTIAL_DATA,
                            "https://scholar.google.com/scholar?cites=1234567890"
                    )
            );
        }

        if ("Institutional Repository Integration in University Systems"
                .equalsIgnoreCase(publication.title())) {
            return List.of(
                    new CitingDocument(
                            "Digital Repositories in European Universities",
                            List.of("Andrea Riva", "Marta Sarti"),
                            2024,
                            null,
                            SourceType.SCHOLAR,
                            RecordStatus.PARTIAL_DATA,
                            "https://scholar.google.com/scholar?cites=987654321"
                    )
            );
        }

        return List.of();
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
                    SourceType.SCHOLAR,
                    RecordStatus.COMPLETE
            ));
        }

        return Optional.empty();
    }
}