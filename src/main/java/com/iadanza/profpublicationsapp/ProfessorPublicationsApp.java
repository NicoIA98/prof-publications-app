package com.iadanza.profpublicationsapp;

import com.iadanza.profpublicationsapp.application.service.ProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultPublicationCatalogService;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.fake.FakeIrisConnector;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

/**
 * Classe principale dell'applicazione JavaFX.
 * In questa fase iniziale mostra un flusso minimo funzionante:
 * ricerca di un professore mock da IRIS e refresh manuale
 * delle pubblicazioni canoniche da IRIS.
 */
public class ProfessorPublicationsApp extends Application {

    private ProfessorSearchService professorSearchService;
    private PublicationCatalogService publicationCatalogService;

    private Professor selectedProfessor;
    private TextArea outputArea;

    @Override
    public void start(Stage stage) {
        IrisConnector irisConnector = new FakeIrisConnector();
        this.professorSearchService = new DefaultProfessorSearchService(irisConnector);
        this.publicationCatalogService = new DefaultPublicationCatalogService(irisConnector);

        Button searchProfessorButton = new Button("Cerca demo professore");
        Button refreshPublicationsButton = new Button("Refresh pubblicazioni da IRIS");

        searchProfessorButton.setOnAction(event -> searchDemoProfessor());
        refreshPublicationsButton.setOnAction(event -> refreshProfessorPublications());

        HBox buttonBar = new HBox(10, searchProfessorButton, refreshPublicationsButton);

        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setWrapText(true);
        outputArea.setPrefRowCount(25);
        outputArea.setText("Applicazione avviata.\nPremi \"Cerca demo professore\" per caricare Mario Rossi.");

        VBox root = new VBox(15, buttonBar, outputArea);
        root.setPadding(new Insets(20));

        Scene scene = new Scene(root, 1000, 700);

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
        builder.append("Pubblicazioni IRIS aggiornate per ").append(selectedProfessor.fullName()).append(":\n\n");

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
        }

        outputArea.setText(builder.toString());
    }

    public static void main(String[] args) {
        launch(args);
    }
}