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
import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.ProfessorLookupEntry;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.config.DotenvLoader;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisAccessMode;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisRestAuthSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisRuntimeSettings;
import com.iadanza.profpublicationsapp.infrastructure.connector.HybridIrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScholarConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScopusConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.fake.FakeIrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.fake.FakeScholarConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.fake.FakeScopusConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.RealIrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic.AuthenticatedRestCallResult;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic.IrisRestAdvancedProbe;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic.RestEndpointProbeResult;
import com.iadanza.profpublicationsapp.infrastructure.lookup.CsvProfessorLookupRepository;
import com.iadanza.profpublicationsapp.infrastructure.lookup.ProfessorLookupRepository;
import com.iadanza.profpublicationsapp.infrastructure.persistence.CitationCacheRepository;
import com.iadanza.profpublicationsapp.infrastructure.persistence.PublicationCacheRepository;
import com.iadanza.profpublicationsapp.infrastructure.persistence.SqliteCitationCacheRepository;
import com.iadanza.profpublicationsapp.infrastructure.persistence.SqlitePublicationCacheRepository;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Classe principale dell'applicazione JavaFX.
 *
 * D5:
 * - rubrica CF privacy-friendly;
 * - il CSV versionato deve essere vuoto, con sola intestazione;
 * - la rubrica si apre anche senza docenti;
 * - pulsante Aiuto nella Rubrica CF;
 * - dati salvati solo nel CSV locale dell'utente.
 */
public class ProfessorPublicationsApp extends Application {

    private ProfessorSearchService professorSearchService;
    private PublicationCatalogService publicationCatalogService;
    private CitationService citationService;
    private CitationRefreshService citationRefreshService;
    private BibtexService bibtexService;
    private ProfessorLookupRepository professorLookupRepository;

    private Professor selectedProfessor;

    private final ObservableList<Publication> publicationItems = FXCollections.observableArrayList();
    private final List<Publication> allPublicationItems = new ArrayList<>();

    private ComboBox<String> searchModeCombo;
    private TextField searchInputField;
    private TextField publicationFilterField;

    private Label professorNameValue;
    private Label professorAffiliationValue;
    private TextArea professorIdentifiersArea;

    private TableView<Publication> publicationsTable;

    private TextArea publicationDetailsArea;
    private TextArea citationDetailsArea;

    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .build();

        IrisRuntimeSettings irisRuntimeSettings = new IrisRuntimeSettings(
                "https://iris.unicas.it",
                IrisAccessMode.AUTO,
                "/api/discover/search/objects",
                "/oai/request?verb=Identify",
                15,
                true
        );

        String irisRestBaseUrl = DotenvLoader.getOrDefault(
                "IRIS_REST_BASE_URL",
                "https://iris.unicas.it:443/"
        );

        String irisRestPathIr = DotenvLoader.getOrDefault(
                "IRIS_REST_PATH_IR",
                "rest/api/v1/"
        );

        String irisRestPathRm = DotenvLoader.getOrDefault(
                "IRIS_REST_PATH_RM",
                "rm/restservices/api/v1"
        );

        String irisRestUsername = DotenvLoader.getOrDefault(
                "IRIS_REST_USERNAME",
                "restadmin"
        );

        String irisRestPassword = DotenvLoader.getOrDefault(
                "IRIS_REST_PASSWORD",
                ""
        );

        int irisRestTimeoutSeconds = DotenvLoader.getIntOrDefault(
                "IRIS_REST_TIMEOUT_SECONDS",
                15
        );

        IrisRestAuthSettings irisRestAuthSettings = new IrisRestAuthSettings(
                irisRestBaseUrl,
                irisRestPathIr,
                irisRestPathRm,
                irisRestUsername,
                irisRestPassword,
                irisRestTimeoutSeconds
        );

        System.out.println("IRIS REST credentials loaded. Username configured: "
                + !irisRestUsername.isBlank()
                + ", password configured: "
                + !irisRestPassword.isBlank());

        RealIrisConnector realIrisConnector =
                new RealIrisConnector(httpClient, irisRuntimeSettings, irisRestAuthSettings);

        System.out.println("=== IRIS REAL PROBE ===");
        System.out.println("Base URL: " + realIrisConnector.getProbeResult().baseUrl());
        System.out.println("REST status: " + realIrisConnector.getProbeResult().restStatusCode());
        System.out.println("OAI status: " + realIrisConnector.getProbeResult().oaiStatusCode());
        System.out.println("REST supported: " + realIrisConnector.getProbeResult().restSupported());
        System.out.println("OAI supported: " + realIrisConnector.getProbeResult().oaiSupported());
        System.out.println("Capabilities: " + realIrisConnector.getProbeResult().capabilities());
        System.out.println("Notes: " + realIrisConnector.getProbeResult().notes());
        System.out.println("=======================");

        IrisRestAdvancedProbe irisRestAdvancedProbe =
                new IrisRestAdvancedProbe(
                        HttpClient.newBuilder()
                                .connectTimeout(java.time.Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NEVER)
                                .build(),
                        "https://iris.unicas.it"
                );

        System.out.println("=== IRIS REST ADVANCED PROBE ===");
        for (RestEndpointProbeResult result : irisRestAdvancedProbe.probeAll()) {
            System.out.println("Method: " + result.method());
            System.out.println("Path: " + result.path());
            System.out.println("Status: " + result.statusCode());
            System.out.println("Redirected: " + result.redirected());
            System.out.println("Auth likely required: " + result.authLikelyRequired());
            System.out.println("Endpoint exists likely: " + result.endpointExistsLikely());
            System.out.println("Location: " + result.locationHeader());
            System.out.println("Content-Type: " + result.contentType());
            System.out.println("Notes: " + result.notes());
            System.out.println("Body preview: " + result.bodyPreview());
            System.out.println("--------------------------------");
        }
        System.out.println("================================");

        System.out.println("=== IRIS AUTHENTICATED REST TESTS ===");
        printAuthenticatedResult(realIrisConnector.probeAuthenticatedIrEcho());
        printAuthenticatedResult(realIrisConnector.probeAuthenticatedRmEcho());
        printAuthenticatedResult(realIrisConnector.probeAuthenticatedPersonByCrisId("rp00418"));
        printAuthenticatedResult(realIrisConnector.probeAuthenticatedItemsByContextUser("rp00418"));
        printAuthenticatedResult(realIrisConnector.probeAuthenticatedItemsByContextUserAndYear("rp00418", "2024"));
        System.out.println("=====================================");

        IrisConnector fakeIrisConnector = new FakeIrisConnector();
        IrisConnector irisConnector = new HybridIrisConnector(fakeIrisConnector, realIrisConnector);

        ScopusConnector scopusConnector = new FakeScopusConnector();
        ScholarConnector scholarConnector = new FakeScholarConnector();

        PublicationCacheRepository publicationCacheRepository =
                new SqlitePublicationCacheRepository("jdbc:sqlite:prof-publications.db");

        CitationCacheRepository citationCacheRepository =
                new SqliteCitationCacheRepository("jdbc:sqlite:prof-publications.db");

        this.professorSearchService = new DefaultProfessorSearchService(irisConnector);
        this.publicationCatalogService =
                new DefaultPublicationCatalogService(irisConnector, publicationCacheRepository);

        DefaultCitationService defaultCitationService =
                new DefaultCitationService(scopusConnector, scholarConnector, citationCacheRepository);

        this.citationService = defaultCitationService;
        this.citationRefreshService =
                new DefaultCitationRefreshService(publicationCatalogService, defaultCitationService);

        this.bibtexService = new DefaultBibtexService(irisConnector, scopusConnector, scholarConnector);

        this.professorLookupRepository =
                new CsvProfessorLookupRepository("/lookup/professors-cf.csv");

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setPadding(new Insets(15));

        root.setTop(buildTopBar());
        root.setCenter(buildMainContent());

        resetProfessorSection();
        resetPublicationDetails();
        resetCitationDetails();
        updateStatus("Applicazione avviata. Cerca per testo libero, ORCID, IRIS ID o Codice fiscale.");

        Scene scene = new Scene(root, 1320, 780);
        applyStylesheet(scene);
        applyApplicationIcon(stage);

        stage.setTitle("Professor Publications App");
        stage.setScene(scene);
        stage.show();
    }

    private void applyStylesheet(Scene scene) {
        URL stylesheet = getClass().getResource("/styles/app.css");

        if (stylesheet == null) {
            System.out.println("Stylesheet non trovato: /styles/app.css");
            return;
        }

        scene.getStylesheets().add(stylesheet.toExternalForm());
    }

    private void applyApplicationIcon(Stage stage) {
        URL iconUrl = getClass().getResource("/icons/app-icon.png");

        if (iconUrl == null) {
            System.out.println("Icona applicazione non trovata: /icons/app-icon.png");
            return;
        }

        stage.getIcons().add(new Image(iconUrl.toExternalForm()));
    }

    private void applyDialogIcon(Dialog<?> dialog) {
        URL iconUrl = getClass().getResource("/icons/app-icon.png");

        if (iconUrl == null) {
            System.out.println("Icona dialog non trovata: /icons/app-icon.png");
            return;
        }

        dialog.setOnShown(event -> {
            Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
            dialogStage.getIcons().add(new Image(iconUrl.toExternalForm()));
        });
    }

    private void printAuthenticatedResult(AuthenticatedRestCallResult result) {
        System.out.println("Method: " + result.method());
        System.out.println("Path: " + result.path());
        System.out.println("Status: " + result.statusCode());
        System.out.println("Content-Type: " + result.contentType());
        System.out.println("Notes: " + result.notes());
        System.out.println("Body preview: " + result.bodyPreview());
        System.out.println("--------------------------------");
    }

    private VBox buildTopBar() {
        Label searchModeLabel = new Label("Ricerca:");

        searchModeCombo = new ComboBox<>();
        searchModeCombo.getItems().addAll(
                "Testo libero",
                "ORCID",
                "IRIS ID",
                "Codice fiscale"
        );
        searchModeCombo.getSelectionModel().selectFirst();
        searchModeCombo.setPrefWidth(160);

        searchInputField = new TextField();
        searchInputField.setPromptText("Es. Mario Rossi / rp00418 / codice fiscale");
        searchInputField.setPrefWidth(340);
        searchInputField.setOnAction(event -> searchProfessor());

        Button searchProfessorButton = new Button("Cerca professore");
        searchProfessorButton.getStyleClass().add("primary-button");

        Button refreshPublicationsButton = new Button("Refresh pubblicazioni da IRIS");
        refreshPublicationsButton.getStyleClass().add("secondary-button");

        Button refreshCitationsButton = new Button("Refresh indici Scopus e Scholar");
        refreshCitationsButton.getStyleClass().add("secondary-button");

        Button professorLookupButton = new Button("Rubrica CF");
        professorLookupButton.getStyleClass().add("success-button");

        searchProfessorButton.setOnAction(event -> searchProfessor());
        refreshPublicationsButton.setOnAction(event -> refreshProfessorPublications());
        refreshCitationsButton.setOnAction(event -> refreshCitationData());
        professorLookupButton.setOnAction(event -> showProfessorLookupDialog());

        HBox controlsBar = new HBox(
                10,
                searchModeLabel,
                searchModeCombo,
                searchInputField,
                searchProfessorButton,
                refreshPublicationsButton,
                refreshCitationsButton,
                professorLookupButton
        );
        controlsBar.getStyleClass().add("search-bar");
        controlsBar.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("Stato applicazione");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        VBox topBar = new VBox(8, controlsBar, statusLabel);
        topBar.getStyleClass().add("top-bar");
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
        sectionTitle.getStyleClass().add("section-title");

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
        professorPane.getStyleClass().add("panel");
        professorPane.setPadding(new Insets(10));
        VBox.setVgrow(professorIdentifiersArea, Priority.ALWAYS);
        return professorPane;
    }

    private VBox buildPublicationsPane() {
        Label sectionTitle = new Label("Pubblicazioni IRIS");
        sectionTitle.getStyleClass().add("section-title");

        publicationFilterField = new TextField();
        publicationFilterField.setPromptText("Filtra pubblicazioni per titolo, anno, autore, venue o DOI");
        publicationFilterField.getStyleClass().add("publication-filter");
        publicationFilterField.textProperty().addListener((obs, oldValue, newValue) -> applyPublicationFilter());

        publicationsTable = new TableView<>();
        publicationsTable.getStyleClass().add("publications-table");
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
                bibtexButton.getStyleClass().add("secondary-button");
                bibtexButton.setOnAction(event -> {
                    Publication publication = getTableView().getItems().get(getIndex());
                    showBibtexForPublication(publication);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                bibtexButton.getStyleClass().remove("bibtex-selected-button");

                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                Publication rowPublication = getTableView().getItems().get(getIndex());
                Publication selectedPublication = getTableView().getSelectionModel().getSelectedItem();

                if (rowPublication != null && rowPublication.equals(selectedPublication)) {
                    if (!bibtexButton.getStyleClass().contains("bibtex-selected-button")) {
                        bibtexButton.getStyleClass().add("bibtex-selected-button");
                    }
                }

                setGraphic(bibtexButton);
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

        publicationsTable.getColumns().add(titleColumn);
        publicationsTable.getColumns().add(yearColumn);
        publicationsTable.getColumns().add(bibtexColumn);
        publicationsTable.getColumns().add(venueColumn);
        publicationsTable.getColumns().add(doiColumn);

        publicationsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                showPublicationDetails(newValue);
                showCitationDetails(newValue);
            } else {
                resetPublicationDetails();
                resetCitationDetails();
            }

            publicationsTable.refresh();
        });

        VBox publicationsPane = new VBox(8, sectionTitle, publicationFilterField, publicationsTable);
        publicationsPane.getStyleClass().add("panel");
        publicationsPane.setPadding(new Insets(10));
        VBox.setVgrow(publicationsTable, Priority.ALWAYS);
        return publicationsPane;
    }

    private VBox buildDetailsPane() {
        Label publicationTitle = new Label("Dettaglio pubblicazione");
        publicationTitle.getStyleClass().add("section-title");

        publicationDetailsArea = new TextArea();
        publicationDetailsArea.setEditable(false);
        publicationDetailsArea.setWrapText(true);
        publicationDetailsArea.setPrefRowCount(12);

        Label citationTitle = new Label("Citazioni e documenti citanti");
        citationTitle.getStyleClass().add("section-title");

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
        detailsPane.getStyleClass().add("panel");
        detailsPane.setPadding(new Insets(10));

        VBox.setVgrow(publicationDetailsArea, Priority.ALWAYS);
        VBox.setVgrow(citationDetailsArea, Priority.ALWAYS);

        return detailsPane;
    }

    private void showProfessorLookupDialog() {
        AtomicReference<List<ProfessorLookupEntry>> allEntries =
                new AtomicReference<>(professorLookupRepository.findAll());

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Rubrica CF");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        applyDialogIcon(dialog);

        Label infoLabel = new Label(
                "Rubrica locale personale. I dati inseriti restano salvati solo su questo PC. "
                        + "Archivio locale: "
                        + professorLookupRepository.getStoragePath()
        );
        infoLabel.setWrapText(true);

        TextField filterField = new TextField();
        filterField.setPromptText("Filtra per nome, cognome o codice fiscale");
        filterField.setPrefWidth(420);
        HBox.setHgrow(filterField, Priority.ALWAYS);

        ObservableList<ProfessorLookupEntry> filteredEntries =
                FXCollections.observableArrayList(allEntries.get());

        TableView<ProfessorLookupEntry> lookupTable = new TableView<>();
        lookupTable.setItems(filteredEntries);
        lookupTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        lookupTable.setPrefSize(860, 430);

        TableColumn<ProfessorLookupEntry, String> nameColumn = new TableColumn<>("Nome");
        nameColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().nome())
        );
        nameColumn.setPrefWidth(220);

        TableColumn<ProfessorLookupEntry, String> surnameColumn = new TableColumn<>("Cognome");
        surnameColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().cognome())
        );
        surnameColumn.setPrefWidth(260);

        TableColumn<ProfessorLookupEntry, String> fiscalCodeColumn = new TableColumn<>("Codice Fiscale");
        fiscalCodeColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(cellData.getValue().codiceFiscale())
        );
        fiscalCodeColumn.setPrefWidth(260);

        lookupTable.getColumns().add(nameColumn);
        lookupTable.getColumns().add(surnameColumn);
        lookupTable.getColumns().add(fiscalCodeColumn);

        Button useButton = new Button("Usa per ricerca");
        useButton.getStyleClass().add("primary-button");
        useButton.setDisable(true);

        Button addButton = new Button("Aggiungi docente");
        addButton.getStyleClass().add("success-button");

        Button helpButton = new Button("Aiuto");
        helpButton.getStyleClass().add("secondary-button");

        HBox lookupToolbar = new HBox(10, filterField, useButton, addButton, helpButton);
        lookupToolbar.setAlignment(Pos.CENTER_LEFT);

        lookupTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) ->
                useButton.setDisable(newValue == null)
        );

        filterField.textProperty().addListener((obs, oldValue, newValue) ->
                refreshLookupTable(filteredEntries, allEntries.get(), newValue)
        );

        lookupTable.setRowFactory(table -> {
            TableRow<ProfessorLookupEntry> row = new TableRow<>();

            MenuItem editItem = new MenuItem("Modifica dati docente");
            MenuItem deleteItem = new MenuItem("Elimina dati docente");
            ContextMenu contextMenu = new ContextMenu(
                    editItem,
                    new SeparatorMenuItem(),
                    deleteItem
            );

            editItem.setOnAction(event -> {
                ProfessorLookupEntry selectedEntry = row.getItem();

                if (selectedEntry == null) {
                    return;
                }

                Optional<ProfessorLookupEntry> editedEntry =
                        showProfessorLookupEntryEditorDialog("Modifica docente", selectedEntry);

                if (editedEntry.isEmpty()) {
                    return;
                }

                try {
                    professorLookupRepository.update(selectedEntry, editedEntry.get());
                    allEntries.set(professorLookupRepository.findAll());
                    refreshLookupTable(filteredEntries, allEntries.get(), filterField.getText());

                    updateStatus("Docente aggiornato nella rubrica CF: "
                            + editedEntry.get().nome()
                            + " "
                            + editedEntry.get().cognome()
                            + ".");
                } catch (IOException | IllegalArgumentException e) {
                    showErrorAlert("Errore modifica docente", e.getMessage());
                }
            });

            deleteItem.setOnAction(event -> {
                ProfessorLookupEntry selectedEntry = row.getItem();

                if (selectedEntry == null) {
                    return;
                }

                boolean confirmed = confirmDeleteProfessorLookupEntry(selectedEntry);

                if (!confirmed) {
                    updateStatus("Eliminazione docente annullata.");
                    return;
                }

                try {
                    professorLookupRepository.delete(selectedEntry);
                    allEntries.set(professorLookupRepository.findAll());
                    refreshLookupTable(filteredEntries, allEntries.get(), filterField.getText());

                    updateStatus("Docente eliminato dalla rubrica CF: "
                            + selectedEntry.nome()
                            + " "
                            + selectedEntry.cognome()
                            + ".");
                } catch (IOException | IllegalArgumentException e) {
                    showErrorAlert("Errore eliminazione docente", e.getMessage());
                }
            });

            row.emptyProperty().addListener((obs, wasEmpty, isEmpty) ->
                    row.setContextMenu(isEmpty ? null : contextMenu)
            );

            return row;
        });

        useButton.setOnAction(event -> {
            ProfessorLookupEntry selectedEntry = lookupTable.getSelectionModel().getSelectedItem();

            if (selectedEntry == null) {
                updateStatus("Seleziona una riga dalla rubrica CF.");
                return;
            }

            searchModeCombo.getSelectionModel().select("Codice fiscale");
            searchInputField.setText(selectedEntry.codiceFiscale());
            dialog.close();

            updateStatus("Codice fiscale selezionato dalla rubrica: "
                    + selectedEntry.nome() + " "
                    + selectedEntry.cognome() + ".");

            searchProfessor();
        });

        addButton.setOnAction(event -> {
            Optional<ProfessorLookupEntry> newEntry =
                    showProfessorLookupEntryEditorDialog("Aggiungi docente", null);

            if (newEntry.isEmpty()) {
                return;
            }

            try {
                professorLookupRepository.add(newEntry.get());
                allEntries.set(professorLookupRepository.findAll());
                refreshLookupTable(filteredEntries, allEntries.get(), filterField.getText());

                updateStatus("Docente aggiunto alla rubrica CF: "
                        + newEntry.get().nome()
                        + " "
                        + newEntry.get().cognome()
                        + ".");
            } catch (IOException | IllegalArgumentException e) {
                showErrorAlert("Errore aggiunta docente", e.getMessage());
            }
        });

        helpButton.setOnAction(event -> showProfessorLookupHelpDialog());

        VBox content = new VBox(10, infoLabel, lookupToolbar, lookupTable);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(10));
        VBox.setVgrow(lookupTable, Priority.ALWAYS);

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void showProfessorLookupHelpDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Aiuto Rubrica CF");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        applyDialogIcon(dialog);

        TextArea helpArea = new TextArea();
        helpArea.setEditable(false);
        helpArea.setWrapText(true);
        helpArea.setPrefSize(640, 420);

        helpArea.setText("""
                Rubrica CF - Guida rapida

                La Rubrica CF è una rubrica locale e personale.
                Per motivi di privacy, l'applicazione viene distribuita con una rubrica vuota.
                I codici fiscali dei docenti non vengono pubblicati nel progetto e non vengono caricati da Git.

                Dove vengono salvati i dati?
                I docenti che inserisci vengono salvati solo sul tuo PC, nel file locale:

                %s

                Come aggiungere un docente
                1. Premi "Aggiungi docente".
                2. Inserisci Nome, Cognome e Codice Fiscale.
                3. Premi "Salva".
                4. Il docente comparirà nella tabella.

                Come modificare un docente
                1. Fai tasto destro sulla riga del docente.
                2. Seleziona "Modifica dati docente".
                3. Correggi i dati e premi "Salva".

                Come eliminare un docente
                1. Fai tasto destro sulla riga del docente.
                2. Seleziona "Elimina dati docente".
                3. Conferma l'eliminazione.

                Come cercare un docente
                1. Usa il filtro in alto per cercare per nome, cognome o codice fiscale.
                2. Seleziona la riga.
                3. Premi "Usa per ricerca".
                4. L'app imposterà automaticamente la ricerca per Codice fiscale.

                Nota importante
                Inserisci solo dati che sei autorizzato a utilizzare.
                I dati restano locali e non vengono inviati a Git.
                """.formatted(professorLookupRepository.getStoragePath()));

        VBox content = new VBox(10, helpArea);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(10));
        VBox.setVgrow(helpArea, Priority.ALWAYS);

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private boolean confirmDeleteProfessorLookupEntry(ProfessorLookupEntry entry) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Elimina docente");
        alert.setHeaderText("Vuoi eliminare questo docente dalla rubrica?");
        alert.setContentText(
                entry.nome()
                        + " "
                        + entry.cognome()
                        + "\nCodice fiscale: "
                        + entry.codiceFiscale()
        );

        applyDialogIcon(alert);

        Optional<ButtonType> result = alert.showAndWait();

        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private Optional<ProfessorLookupEntry> showProfessorLookupEntryEditorDialog(
            String title,
            ProfessorLookupEntry initialValue
    ) {
        Dialog<ProfessorLookupEntry> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setResizable(true);
        applyDialogIcon(dialog);

        ButtonType saveButtonType = new ButtonType("Salva", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Nome");

        TextField surnameField = new TextField();
        surnameField.setPromptText("Cognome");

        TextField fiscalCodeField = new TextField();
        fiscalCodeField.setPromptText("Codice fiscale");

        if (initialValue != null) {
            nameField.setText(initialValue.nome());
            surnameField.setText(initialValue.cognome());
            fiscalCodeField.setText(initialValue.codiceFiscale());
        }

        fiscalCodeField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }

            String upper = newValue.toUpperCase().trim();

            if (!upper.equals(newValue)) {
                fiscalCodeField.setText(upper);
            }
        });

        VBox content = new VBox(
                8,
                new Label("Nome"),
                nameField,
                new Label("Cognome"),
                surnameField,
                new Label("Codice fiscale"),
                fiscalCodeField
        );
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(10));
        content.setPrefWidth(420);

        dialog.getDialogPane().setContent(content);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String validationError = validateLookupInput(
                    nameField.getText(),
                    surnameField.getText(),
                    fiscalCodeField.getText()
            );

            if (validationError != null) {
                showErrorAlert("Dati non validi", validationError);
                event.consume();
            }
        });

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveButtonType) {
                return null;
            }

            return new ProfessorLookupEntry(
                    nameField.getText(),
                    surnameField.getText(),
                    fiscalCodeField.getText()
            );
        });

        return dialog.showAndWait();
    }

    private String validateLookupInput(String nome, String cognome, String codiceFiscale) {
        if (nome == null || nome.trim().isBlank()) {
            return "Il nome è obbligatorio.";
        }

        if (cognome == null || cognome.trim().isBlank()) {
            return "Il cognome è obbligatorio.";
        }

        if (codiceFiscale == null || codiceFiscale.trim().isBlank()) {
            return "Il codice fiscale è obbligatorio.";
        }

        String normalizedFiscalCode = codiceFiscale.trim().toUpperCase();

        if (!normalizedFiscalCode.matches("[A-Z0-9]{16}")) {
            return "Il codice fiscale deve contenere 16 caratteri alfanumerici.";
        }

        return null;
    }

    private void refreshLookupTable(
            ObservableList<ProfessorLookupEntry> filteredEntries,
            List<ProfessorLookupEntry> allEntries,
            String query
    ) {
        String normalizedQuery = query != null ? query.trim() : "";

        if (normalizedQuery.isBlank()) {
            filteredEntries.setAll(allEntries);
            return;
        }

        filteredEntries.setAll(
                allEntries.stream()
                        .filter(entry -> entry.matches(normalizedQuery))
                        .toList()
        );
    }

    private void applyPublicationFilter() {
        if (publicationFilterField == null) {
            publicationItems.setAll(allPublicationItems);
            return;
        }

        String query = publicationFilterField.getText() != null
                ? publicationFilterField.getText().trim().toLowerCase()
                : "";

        if (query.isBlank()) {
            publicationItems.setAll(allPublicationItems);
            selectFirstPublicationIfAvailable();
            return;
        }

        List<Publication> filteredPublications = allPublicationItems.stream()
                .filter(publication -> publicationMatchesFilter(publication, query))
                .toList();

        publicationItems.setAll(filteredPublications);
        selectFirstPublicationIfAvailable();
    }

    private boolean publicationMatchesFilter(Publication publication, String query) {
        if (publication == null) {
            return false;
        }

        String title = publication.title() != null ? publication.title().toLowerCase() : "";
        String year = publication.year() != null ? String.valueOf(publication.year()) : "";
        String venue = publication.venue() != null ? publication.venue().toLowerCase() : "";
        String doi = publication.doi() != null ? publication.doi().toLowerCase() : "";
        String authors = publication.authors() != null
                ? String.join(" ", publication.authors()).toLowerCase()
                : "";

        return title.contains(query)
                || year.contains(query)
                || venue.contains(query)
                || doi.contains(query)
                || authors.contains(query);
    }

    private void setPublicationItems(List<Publication> publications) {
        allPublicationItems.clear();
        allPublicationItems.addAll(publications);

        if (publicationFilterField != null) {
            publicationFilterField.clear();
        }

        publicationItems.setAll(allPublicationItems);
        selectFirstPublicationIfAvailable();
    }

    private void selectFirstPublicationIfAvailable() {
        if (!publicationItems.isEmpty()) {
            publicationsTable.getSelectionModel().selectFirst();
        } else {
            resetPublicationDetails();
            resetCitationDetails();
        }

        publicationsTable.refresh();
    }

    private void searchProfessor() {
        String query = searchInputField.getText() != null ? searchInputField.getText().trim() : "";
        String selectedMode = searchModeCombo.getValue();

        if (query.isBlank()) {
            updateStatus("Inserisci un valore di ricerca.");
            return;
        }

        List<Professor> results;

        if ("Testo libero".equals(selectedMode)) {
            results = professorSearchService.searchByFreeText(query);
        } else {
            IdentifierType identifierType = mapSearchModeToIdentifierType(selectedMode);
            Optional<Professor> professor = professorSearchService.findByIdentifier(identifierType, query);
            results = professor.map(List::of).orElse(List.of());
        }

        if (results.isEmpty()) {
            selectedProfessor = null;
            publicationItems.clear();
            allPublicationItems.clear();

            if (publicationFilterField != null) {
                publicationFilterField.clear();
            }

            resetProfessorSection();
            resetPublicationDetails();
            resetCitationDetails();
            updateStatus("Nessun professore trovato per la ricerca: " + query);
            return;
        }

        selectedProfessor = results.get(0);
        showProfessorDetails(selectedProfessor);

        List<Publication> cachedPublications = sortPublicationsByYearDesc(
                publicationCatalogService.getCachedPublications(selectedProfessor)
        );

        setPublicationItems(cachedPublications);

        updateStatus("Professore caricato. Pubblicazioni in cache: " + publicationItems.size() + ".");
    }

    private IdentifierType mapSearchModeToIdentifierType(String searchMode) {
        return switch (searchMode) {
            case "ORCID" -> IdentifierType.ORCID;
            case "IRIS ID" -> IdentifierType.IRIS_ID;
            case "Codice fiscale" -> IdentifierType.CODICE_FISCALE;
            default -> IdentifierType.ORCID;
        };
    }

    private void refreshProfessorPublications() {
        if (selectedProfessor == null) {
            updateStatus("Prima devi cercare un professore.");
            return;
        }

        List<Publication> publications = sortPublicationsByYearDesc(
                publicationCatalogService.refreshPublicationsFromIris(selectedProfessor)
        );

        setPublicationItems(publications);

        if (!publicationItems.isEmpty()) {
            updateStatus("Pubblicazioni IRIS aggiornate: " + publications.size() + ".");
            askForCitationRefresh();
        } else {
            updateStatus("Nessuna pubblicazione trovata su IRIS.");
        }
    }

    private List<Publication> sortPublicationsByYearDesc(List<Publication> publications) {
        return publications.stream()
                .sorted(
                        Comparator.comparing(
                                Publication::year,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ).thenComparing(
                                Publication::title,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                        )
                )
                .toList();
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
        applyDialogIcon(alert);

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
        dialog.setResizable(true);
        applyDialogIcon(dialog);

        TextArea bibtexArea = new TextArea(bibtexEntry.rawBibtex());
        bibtexArea.setEditable(false);
        bibtexArea.setWrapText(true);
        bibtexArea.setPrefSize(700, 350);

        Button copyButton = new Button("Copia BibTeX");
        copyButton.getStyleClass().add("primary-button");
        copyButton.setOnAction(event -> copyBibtexToClipboard(bibtexEntry.rawBibtex()));

        Button saveButton = new Button("Salva .bib");
        saveButton.getStyleClass().add("success-button");
        saveButton.setOnAction(event -> saveBibtexToFile(bibtexEntry));

        HBox actionBar = new HBox(10, copyButton, saveButton);
        actionBar.getStyleClass().add("dialog-actions");
        actionBar.setAlignment(Pos.CENTER_LEFT);

        VBox dialogContent = new VBox(10, bibtexArea, actionBar);
        dialogContent.getStyleClass().add("dialog-content");
        dialogContent.setPadding(new Insets(10));
        VBox.setVgrow(bibtexArea, Priority.ALWAYS);

        dialog.getDialogPane().setContent(dialogContent);
        dialog.showAndWait();
    }

    private void copyBibtexToClipboard(String bibtexText) {
        ClipboardContent content = new ClipboardContent();
        content.putString(bibtexText);
        Clipboard.getSystemClipboard().setContent(content);
        updateStatus("BibTeX copiato negli appunti.");
    }

    private void saveBibtexToFile(BibtexEntry bibtexEntry) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Salva file BibTeX");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("BibTeX files (*.bib)", "*.bib")
        );

        String suggestedFileName = bibtexEntry.citationKey() != null && !bibtexEntry.citationKey().isBlank()
                ? bibtexEntry.citationKey() + ".bib"
                : "citation.bib";

        fileChooser.setInitialFileName(suggestedFileName);

        Stage currentStage = (Stage) publicationsTable.getScene().getWindow();
        File selectedFile = fileChooser.showSaveDialog(currentStage);

        if (selectedFile == null) {
            updateStatus("Salvataggio BibTeX annullato.");
            return;
        }

        try {
            Files.writeString(selectedFile.toPath(), bibtexEntry.rawBibtex());
            updateStatus("BibTeX salvato in: " + selectedFile.getAbsolutePath());
        } catch (IOException e) {
            updateStatus("Errore durante il salvataggio del file BibTeX.");
            showErrorAlert("Errore salvataggio file", "Impossibile salvare il file .bib selezionato.");
        }
    }

    private void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "Errore non specificato.");
        applyDialogIcon(alert);
        alert.showAndWait();
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
        String authorsText = publication.authors() != null && !publication.authors().isEmpty()
                ? String.join(", ", publication.authors())
                : "N/D";

        StringBuilder builder = new StringBuilder();

        builder.append("Titolo: ").append(publication.title()).append("\n\n");
        builder.append("Autori: ").append(authorsText).append("\n");
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