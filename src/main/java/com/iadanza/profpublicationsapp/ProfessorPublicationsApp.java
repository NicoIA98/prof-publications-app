package com.iadanza.profpublicationsapp;

import com.iadanza.profpublicationsapp.application.service.BibtexService;
import com.iadanza.profpublicationsapp.application.service.CitationRefreshService;
import com.iadanza.profpublicationsapp.application.service.CitationService;
import com.iadanza.profpublicationsapp.application.service.ProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultBibtexService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultCitationRefreshService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultCitationService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultPublicationCatalogService;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
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
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.Optional;

/**
 * Classe principale dell'applicazione JavaFX.
 * Dashboard unica con:
 * - dati del professore
 * - tabella pubblicazioni IRIS
 * - dettaglio pubblicazione
 * - citazioni e documenti citanti
 * - BibTeX richiamabile da ogni riga della tabella
 */
public class ProfessorPublicationsApp extends Application {

    private ProfessorSearchService professorSearchService;
    private PublicationCatalogService publicationCatalogService;
    private CitationService citationService;
    private CitationRefreshService citationRefreshService;
    private BibtexService bibtexService;

    private Professor selectedProfessor;

    private final ObservableList<Publication> publicationItems = FXCollections.observableArrayList();

    private Label professorNameValue;
    private Label professorAffiliationValue;
    private TextArea professorIdentifiersArea;

    private TableView<Publication> publicationsTable;

    private TextArea publicationDetailsArea;
    private TextArea citationDetailsArea;

    private Label statusLabel;

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

        this.bibtexService = new DefaultBibtexService(irisConnector, scopusConnector, scholarConnector);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        root.setTop(buildTopBar());
        root.setCenter(buildMainContent());
        root.setBottom(buildStatusBar());

        resetProfessorSection();
        resetPublicationDetails();
        resetCitationDetails();
        updateStatus("Applicazione avviata. Cerca il professore demo per iniziare.");

        Scene scene = new Scene(root, 1250, 780);
        stage.setTitle("Professor Publications App");
        stage.setScene(scene);
        stage.show();
    }

    private HBox buildTopBar() {
        Button searchProfessorButton = new Button("Cerca demo professore");
        Button refreshPublicationsButton = new Button("Refresh pubblicazioni da IRIS");
        Button refreshCitationsButton = new Button("Refresh indici Scopus e Scholar");

        searchProfessorButton.setOnAction(event -> searchDemoProfessor());
        refreshPublicationsButton.setOnAction(event -> refreshProfessorPublications());
        refreshCitationsButton.setOnAction(event -> refreshCitationData());

        HBox topBar = new HBox(
                10,
                searchProfessorButton,
                refreshPublicationsButton,
                refreshCitationsButton
        );
        topBar.setPadding(new Insets(0, 0, 15, 0));
        return topBar;
    }

    private SplitPane buildMainContent() {
        VBox professorPane = buildProfessorPane();
        VBox publicationsPane = buildPublicationsPane();
        VBox detailsPane = buildDetailsPane();

        SplitPane splitPane = new SplitPane(professorPane, publicationsPane, detailsPane);
        splitPane.setDividerPositions(0.24, 0.62);
        return splitPane;
    }

    private VBox buildProfessorPane() {
        Label sectionTitle = new Label("Professore");
        sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label nameLabel = new Label("Nome:");
        professorNameValue = new Label("-");

        Label affiliationLabel = new Label("Affiliazione:");
        professorAffiliationValue = new Label("-");

        Label identifiersLabel = new Label("Identificativi:");
        professorIdentifiersArea = new TextArea();
        professorIdentifiersArea.setEditable(false);
        professorIdentifiersArea.setWrapText(true);
        professorIdentifiersArea.setPrefRowCount(12);

        VBox professorPane = new VBox(
                8,
                sectionTitle,
                nameLabel, professorNameValue,
                affiliationLabel, professorAffiliationValue,
                identifiersLabel, professorIdentifiersArea
        );
        professorPane.setPadding(new Insets(10));
        VBox.setVgrow(professorIdentifiersArea, Priority.ALWAYS);
        return professorPane;
    }

    private VBox buildPublicationsPane() {
        Label sectionTitle = new Label("Pubblicazioni IRIS");
        sectionTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        publicationsTable = new TableView<>();
        publicationsTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        publicationsTable.setItems(publicationItems);

        TableColumn<Publication, String> titleColumn = new TableColumn<>("Titolo");
        titleColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().title())
        );
        titleColumn.setPrefWidth(320);

        TableColumn<Publication, Integer> yearColumn = new TableColumn<>("Anno");
        yearColumn.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().year())
        );
        yearColumn.setPrefWidth(70);

        TableColumn<Publication, Void> bibtexColumn = new TableColumn<>("BibTeX");
        bibtexColumn.setPrefWidth(85);
        bibtexColumn.setCellFactory(param -> new TableCell<>() {
            private final Button bibtexButton = new Button(".bib");

            {
                bibtexButton.setOnAction(event -> {
                    Publication publication = getTableView().getItems().get(getIndex());
                    showBibtexForPublication(publication);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(bibtexButton);
                }
            }
        });

        TableColumn<Publication, String> venueColumn = new TableColumn<>("Venue");
        venueColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().venue() != null ? cellData.getValue().venue() : ""
                )
        );
        venueColumn.setPrefWidth(220);

        TableColumn<Publication, String> doiColumn = new TableColumn<>("DOI");
        doiColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().doi() != null ? cellData.getValue().doi() : "N/D"
                )
        );
        doiColumn.setPrefWidth(170);

        publicationsTable.getColumns().addAll(
                titleColumn,
                yearColumn,
                bibtexColumn,
                venueColumn,
                doiColumn
        );

        publicationsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                showPublicationDetails(newValue);
                showCitationDetails(newValue);
            } else {
                resetPublicationDetails();
                resetCitationDetails();
            }
        });

        VBox publicationsPane = new VBox(8, sectionTitle, publicationsTable);
        publicationsPane.setPadding(new Insets(10));
        VBox.setVgrow(publicationsTable, Priority.ALWAYS);
        return publicationsPane;
    }

    private VBox buildDetailsPane() {
        Label publicationTitle = new Label("Dettaglio pubblicazione");
        publicationTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        publicationDetailsArea = new TextArea();
        publicationDetailsArea.setEditable(false);
        publicationDetailsArea.setWrapText(true);
        publicationDetailsArea.setPrefRowCount(12);

        Label citationTitle = new Label("Citazioni e documenti citanti");
        citationTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        citationDetailsArea = new TextArea();
        citationDetailsArea.setEditable(false);
        citationDetailsArea.setWrapText(true);
        citationDetailsArea.setPrefRowCount(16);

        VBox detailsPane = new VBox(
                8,
                publicationTitle,
                publicationDetailsArea,
                citationTitle,
                citationDetailsArea
        );
        detailsPane.setPadding(new Insets(10));

        VBox.setVgrow(publicationDetailsArea, Priority.ALWAYS);
        VBox.setVgrow(citationDetailsArea, Priority.ALWAYS);

        return detailsPane;
    }

    private HBox buildStatusBar() {
        statusLabel = new Label("Stato applicazione");
        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(12, 4, 0, 4));
        return statusBar;
    }

    private void searchDemoProfessor() {
        List<Professor> results = professorSearchService.searchByFreeText("Mario Rossi");

        if (results.isEmpty()) {
            selectedProfessor = null;
            publicationItems.clear();
            resetProfessorSection();
            resetPublicationDetails();
            resetCitationDetails();
            updateStatus("Nessun professore trovato.");
            return;
        }

        selectedProfessor = results.get(0);
        showProfessorDetails(selectedProfessor);

        List<Publication> cachedPublications = publicationCatalogService.getCachedPublications(selectedProfessor);
        publicationItems.setAll(cachedPublications);

        if (!publicationItems.isEmpty()) {
            publicationsTable.getSelectionModel().selectFirst();
        } else {
            resetPublicationDetails();
            resetCitationDetails();
        }

        updateStatus("Professore caricato. Pubblicazioni in cache: " + publicationItems.size() + ".");
    }

    private void refreshProfessorPublications() {
        if (selectedProfessor == null) {
            updateStatus("Prima devi cercare un professore.");
            return;
        }

        List<Publication> publications = publicationCatalogService.refreshPublicationsFromIris(selectedProfessor);
        publicationItems.setAll(publications);

        if (!publicationItems.isEmpty()) {
            publicationsTable.getSelectionModel().selectFirst();
            updateStatus("Pubblicazioni IRIS aggiornate: " + publications.size() + ".");
            askForCitationRefresh();
        } else {
            resetPublicationDetails();
            resetCitationDetails();
            updateStatus("Nessuna pubblicazione trovata su IRIS.");
        }
    }

    private void refreshCitationData() {
        if (selectedProfessor == null) {
            updateStatus("Prima devi cercare un professore.");
            return;
        }

        if (publicationItems.isEmpty()) {
            updateStatus("Prima devi aggiornare le pubblicazioni da IRIS.");
            return;
        }

        citationRefreshService.refreshAllCitationData(selectedProfessor);

        Publication selectedPublication = publicationsTable.getSelectionModel().getSelectedItem();
        if (selectedPublication != null) {
            showCitationDetails(selectedPublication);
        }

        updateStatus("Indici citazionali Scopus e Scholar aggiornati.");
    }

    private void askForCitationRefresh() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Aggiornamento indici citazionali");
        alert.setHeaderText("Pubblicazioni IRIS aggiornate");
        alert.setContentText("Vuoi aggiornare ora gli indici citazionali da Scopus e Scholar?");

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            refreshCitationData();
        } else {
            updateStatus("Pubblicazioni IRIS aggiornate. Refresh citazionale rimandato.");
        }
    }

    private void showBibtexForPublication(Publication publication) {
        if (publication == null) {
            updateStatus("Seleziona prima una pubblicazione.");
            return;
        }

        Optional<BibtexEntry> result = bibtexService.resolveBibtex(publication);

        if (result.isEmpty()) {
            updateStatus("Impossibile generare o recuperare il BibTeX.");
            return;
        }

        BibtexEntry bibtexEntry = result.get();

        updateStatus("BibTeX ottenuto da sorgente: " + bibtexEntry.sourceType() + ".");

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("BibTeX - " + publication.title());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextArea bibtexArea = new TextArea(bibtexEntry.rawBibtex());
        bibtexArea.setEditable(false);
        bibtexArea.setWrapText(true);
        bibtexArea.setPrefSize(700, 350);

        dialog.getDialogPane().setContent(bibtexArea);
        dialog.setResizable(true);
        dialog.showAndWait();
    }

    private void showProfessorDetails(Professor professor) {
        professorNameValue.setText(professor.fullName());
        professorAffiliationValue.setText(professor.affiliation());

        StringBuilder identifiersText = new StringBuilder();
        professor.externalIdentifiers().forEach(identifier ->
                identifiersText.append("• ")
                        .append(identifier.type())
                        .append(": ")
                        .append(identifier.value())
                        .append(" [")
                        .append(identifier.sourceType())
                        .append("]\n")
        );

        professorIdentifiersArea.setText(identifiersText.toString());
    }

    private void showPublicationDetails(Publication publication) {
        StringBuilder builder = new StringBuilder();

        builder.append("Titolo: ").append(publication.title()).append("\n\n");
        builder.append("Autori: ").append(String.join(", ", publication.authors())).append("\n");
        builder.append("Anno: ").append(publication.year()).append("\n");
        builder.append("Venue: ").append(publication.venue() != null ? publication.venue() : "N/D").append("\n");
        builder.append("DOI: ").append(publication.doi() != null ? publication.doi() : "N/D").append("\n");
        builder.append("Stato record: ").append(publication.recordStatus()).append("\n");
        builder.append("Sorgente primaria: ").append(publication.primarySource()).append("\n");
        builder.append("URL sorgente: ").append(publication.sourceUrl() != null ? publication.sourceUrl() : "N/D").append("\n\n");

        builder.append("Abstract:\n");
        builder.append(publication.abstractText() != null ? publication.abstractText() : "N/D");

        publicationDetailsArea.setText(builder.toString());
    }

    private void showCitationDetails(Publication publication) {
        CitationSummary summary = citationService.getCachedCitationSummary(publication);
        List<CitingDocument> citingDocuments = citationService.getCachedCitingDocuments(publication);

        StringBuilder builder = new StringBuilder();

        builder.append("Citazioni Scopus: ")
                .append(summary.scopusCitationCount() != null ? summary.scopusCitationCount() : "N/D")
                .append("\n");
        builder.append("Citazioni Scholar: ")
                .append(summary.scholarCitationCount() != null ? summary.scholarCitationCount() : "N/D")
                .append("\n");
        builder.append("Totale aggregato: ")
                .append(summary.totalCitationCount() != null ? summary.totalCitationCount() : "N/D")
                .append("\n\n");

        builder.append("Documenti citanti trovati: ").append(citingDocuments.size()).append("\n\n");

        if (citingDocuments.isEmpty()) {
            builder.append("Nessun dato citazionale in cache.\n");
            builder.append("Premi \"Refresh indici Scopus e Scholar\" per aggiornarli.");
        } else {
            for (CitingDocument document : citingDocuments) {
                builder.append("• ").append(document.title()).append("\n");
                builder.append("  Autori: ")
                        .append(document.authors() != null && !document.authors().isEmpty()
                                ? String.join(", ", document.authors())
                                : "N/D")
                        .append("\n");
                builder.append("  Anno: ").append(document.year() != null ? document.year() : "N/D").append("\n");
                builder.append("  DOI: ").append(document.doi() != null ? document.doi() : "N/D").append("\n");
                builder.append("  Sorgente: ").append(document.sourceType()).append("\n");
                builder.append("  Stato: ").append(document.recordStatus()).append("\n");
                builder.append("  URL: ").append(document.sourceUrl() != null ? document.sourceUrl() : "N/D").append("\n\n");
            }
        }

        citationDetailsArea.setText(builder.toString());
    }

    private void resetProfessorSection() {
        professorNameValue.setText("-");
        professorAffiliationValue.setText("-");
        professorIdentifiersArea.setText("");
    }

    private void resetPublicationDetails() {
        publicationDetailsArea.setText("Seleziona una pubblicazione per visualizzarne il dettaglio.");
    }

    private void resetCitationDetails() {
        citationDetailsArea.setText("Seleziona una pubblicazione e aggiorna gli indici citazionali per vedere i dettagli.");
    }

    private void updateStatus(String statusText) {
        statusLabel.setText(statusText);
    }

    public static void main(String[] args) {
        launch(args);
    }
}