package com.iadanza.profpublicationsapp.infrastructure.connector.fake;

import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScopusConnector;

import java.util.List;
import java.util.Optional;

/**
 * Implementazione fake di ScopusConnector per simulare
 * dati citazionali e documenti citanti.
 */
public class FakeScopusConnector implements ScopusConnector {

    @Override
    public Optional<CitationSummary> fetchCitationSummary(Publication publication) {
        if ("10.1000/jfx-arch-2024".equalsIgnoreCase(publication.doi())) {
            return Optional.of(new CitationSummary(12, null, 12));
        }

        if ("Institutional Repository Integration in University Systems"
                .equalsIgnoreCase(publication.title())) {
            return Optional.of(new CitationSummary(3, null, 3));
        }

        return Optional.empty();
    }

    @Override
    public List<CitingDocument> findCitingDocuments(Publication publication) {
        if ("10.1000/jfx-arch-2024".equalsIgnoreCase(publication.doi())) {
            return List.of(
                    new CitingDocument(
                            "Modern Patterns for JavaFX Information Systems",
                            List.of("Elena Rosa", "Marco Gialli"),
                            2025,
                            "10.1000/modern-jfx-2025",
                            SourceType.SCOPUS,
                            RecordStatus.COMPLETE,
                            "https://www.scopus.com/record/display.uri?eid=2-s2.0-85199999991"
                    ),
                    new CitingDocument(
                            "Desktop Search Applications in Academic Contexts",
                            List.of("Paolo Bini"),
                            2025,
                            "10.1000/desktop-search-2025",
                            SourceType.SCOPUS,
                            RecordStatus.COMPLETE,
                            "https://www.scopus.com/record/display.uri?eid=2-s2.0-85199999992"
                    )
            );
        }

        if ("Institutional Repository Integration in University Systems"
                .equalsIgnoreCase(publication.title())) {
            return List.of(
                    new CitingDocument(
                            "University Repository Governance Models",
                            List.of("Francesca Neri"),
                            2024,
                            null,
                            SourceType.SCOPUS,
                            RecordStatus.PARTIAL_DATA,
                            "https://www.scopus.com/record/display.uri?eid=2-s2.0-85199999993"
                    )
            );
        }

        return List.of();
    }
}