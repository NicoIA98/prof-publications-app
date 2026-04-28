package com.iadanza.profpublicationsapp;

import com.iadanza.profpublicationsapp.application.service.CitationRefreshService;
import com.iadanza.profpublicationsapp.application.service.CitationService;
import com.iadanza.profpublicationsapp.application.service.ProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultCitationRefreshService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultCitationService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultPublicationCatalogService;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScholarConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScopusConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.fake.FakeIrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.fake.FakeScholarConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.fake.FakeScopusConnector;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * Classe principale dell'applicazione JavaFX.
 * In questa fase iniziale mostra un flusso minimo funzionante:
 * ricerca di un professore mock da IRIS, refresh manuale
 * delle pubblicazioni canoniche da IRIS e refresh manuale
 * degli indici citazionali da Scopus e Scholar.
 */
public class ProfessorPublicationsApp extends Application {

    private ProfessorSearchService professorSearchService;
    private PublicationCatalogService publicationCatalogService;
    private CitationService citationService;
    private CitationRefreshService citationRefreshService;

    private Professor selectedProfessor;
    private TextArea outputArea;

    @Override
    public void start(Stage stage) {
        IrisConnector irisConnector = new FakeIrisConnector();
        ScopusConnector scopusConnector = new FakeScopusConnector();
        ScholarConnector scholarConnector = new FakeScholarConnector();

        this.professorSearchService = new DefaultProfessorSearchService(irisConnector);
        this.publicationCatalogService = new DefaultPublicationCatalogService(irisConnector);

        DefaultCitationService defaultCitationService =
                new DefaultCitationService(scopusConnector, scholarConnector);

        this.citationService = defaultCitationService;
        this.citationRefreshService =
                new DefaultCitationRefreshService(publicationCatalogService, defaultCitationService);

        Button searchProfessorButton = new Button("Cerca demo professore");
        Button refreshPublicationsButton = new Button("Refresh pubblicazioni da IRIS");
        Button refreshCitationsButton = new Button("Refresh indici Scopus e Scholar");

        searchProfessorButton.setOnAction(event -> searchDemoProfessor());
        refreshPublicationsButton.setOnAction(event -> refreshProfessorPublications());
        refreshCitationsButton.setOnAction(event -> refreshCitationData());

        HBox buttonBar = new HBox(10, searchProfessorButton, refreshPublicationsButton, refreshCitationsButton);

        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setPrefRowCount(25);
        outputArea.setText("""
                Applicazione avviata.
                Premi "Cerca demo professore" per caricare Mario Rossi.
                """);

        VBox root = new VBox(15, buttonBar, outputArea);
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root, 1100, 750);

        stage.setTitle("Professor Publications App");
        stage.setScene(scene);
        stage.show();
    }

    private void searchDemoProfessor() {
        List<Professor> results = professorSearchService.searchByFreeText("Mario Rossi");

        if (results.isEmpty()) {
            outputArea.setText("Nessun professore trovato.");
            selectedProfessor = null;
            return;
        }

        selectedProfessor = results.get(0);

        StringBuilder builder = new StringBuilder();
        builder.append("Professore trovato:\n");
        builder.append("- Nome: ").append(selectedProfessor.fullName()).append("\n");
        builder.append("- Affiliazione: ").append(selectedProfessor.affiliation()).append("\n");
        builder.append("- Identificativi:\n");

        selectedProfessor.externalIdentifiers().forEach(identifier ->
                builder.append("  • ")
                        .append(identifier.type())
                        .append(": ")
                        .append(identifier.value())
                        .append(" [")
                        .append(identifier.sourceType())
                        .append("]\n")
        );

        List<Publication> cachedPublications = publicationCatalogService.getCachedPublications(selectedProfessor);

        builder.append("\nPubblicazioni in cache: ").append(cachedPublications.size()).append("\n");
        builder.append("Premi \"Refresh pubblicazioni da IRIS\" per aggiornarle.\n");

        outputArea.setText(builder.toString());
    }

    private void refreshProfessorPublications() {
        if (selectedProfessor == null) {
            outputArea.setText("Prima devi cercare un professore.");
            return;
        }

        List<Publication> publications = publicationCatalogService.refreshPublicationsFromIris(selectedProfessor);

        StringBuilder builder = new StringBuilder();
        builder.append("Pubblicazioni IRIS aggiornate per ")
                .append(selectedProfessor.fullName())
                .append(":\n\n");

        if (publications.isEmpty()) {
            builder.append("Nessuna pubblicazione trovata.");
        } else {
            for (int i = 0; i < publications.size(); i++) {
                Publication publication = publications.get(i);

                builder.append(i + 1).append(". ").append(publication.title()).append("\n");
                builder.append("   Anno: ").append(publication.year()).append("\n");
                builder.append("   Venue: ").append(publication.venue()).append("\n");
                builder.append("   DOI: ").append(publication.doi() != null ? publication.doi() : "N/D").append("\n");
                builder.append("   Stato: ").append(publication.recordStatus()).append("\n");
                builder.append("   Sorgente: ").append(publication.primarySource()).append("\n");
                builder.append("\n");
            }

            builder.append("Ora puoi premere \"Refresh indici Scopus e Scholar\".\n");
        }

        outputArea.setText(builder.toString());
    }

    private void refreshCitationData() {
        if (selectedProfessor == null) {
            outputArea.setText("Prima devi cercare un professore.");
            return;
        }

        List<Publication> publications = publicationCatalogService.getCachedPublications(selectedProfessor);

        if (publications.isEmpty()) {
            outputArea.setText("""
                    Non ci sono pubblicazioni in cache.
                    Premi prima "Refresh pubblicazioni da IRIS".
                    """);
            return;
        }

        citationRefreshService.refreshAllCitationData(selectedProfessor);

        StringBuilder builder = new StringBuilder();
        builder.append("Indici citazionali aggiornati per ")
                .append(selectedProfessor.fullName())
                .append(":\n\n");

        for (int i = 0; i < publications.size(); i++) {
            Publication publication = publications.get(i);
            CitationSummary summary = citationService.getCachedCitationSummary(publication);
            List<CitingDocument> citingDocuments = citationService.getCachedCitingDocuments(publication);

            builder.append(i + 1).append(". ").append(publication.title()).append("\n");
            builder.append("   Citazioni Scopus: ")
                    .append(summary.scopusCitationCount() != null ? summary.scopusCitationCount() : "N/D")
                    .append("\n");
            builder.append("   Citazioni Scholar: ")
                    .append(summary.scholarCitationCount() != null ? summary.scholarCitationCount() : "N/D")
                    .append("\n");
            builder.append("   Totale aggregato: ")
                    .append(summary.totalCitationCount() != null ? summary.totalCitationCount() : "N/D")
                    .append("\n");
            builder.append("   Documenti citanti trovati: ")
                    .append(citingDocuments.size())
                    .append("\n");

            if (!citingDocuments.isEmpty()) {
                for (CitingDocument document : citingDocuments) {
                    builder.append("      - ")
                            .append(document.title())
                            .append(" [")
                            .append(document.sourceType())
                            .append("]\n");
                }
            }

            builder.append("\n");
        }

        outputArea.setText(builder.toString());
    }

    public static void main(String[] args) {
        launch(args);
    }
}