package com.iadanza.profpublicationsapp;

import com.iadanza.profpublicationsapp.application.service.BibtexService;
import com.iadanza.profpublicationsapp.application.service.CitationService;
import com.iadanza.profpublicationsapp.application.service.ProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.bootstrap.AppBootstrap;
import com.iadanza.profpublicationsapp.bootstrap.AppServices;
import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.ProfessorLookupEntry;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.config.ConnectionSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.LocalSettingsRepository;
import com.iadanza.profpublicationsapp.infrastructure.lookup.ProfessorLookupRepository;
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
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Classe principale dell'applicazione JavaFX.
 *
 * Stato attuale:
 * - bootstrap infrastrutturale spostato in AppBootstrap;
 * - servizi applicativi ricevuti tramite AppServices;
 * - gestione settings locali tramite LocalSettingsRepository;
 * - UI principale, Rubrica CF, BibTeX e dialog documenti citanti ancora qui.
 */
public class ProfessorPublicationsApp extends Application {

    private ProfessorSearchService professorSearchService;
    private PublicationCatalogService publicationCatalogService;
    private CitationService citationService;
    private BibtexService bibtexService;
    private ProfessorLookupRepository professorLookupRepository;
    private LocalSettingsRepository localSettingsRepository;

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
        AppServices services = AppBootstrap.createServices();

        this.professorSearchService = services.professorSearchService();
        this.publicationCatalogService = services.publicationCatalogService();
        this.citationService = services.citationService();
        this.bibtexService = services.bibtexService();
        this.professorLookupRepository = services.professorLookupRepository();
        this.localSettingsRepository = services.localSettingsRepository();

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

    private void applyDialogStylesheet(Dialog<?> dialog) {
        URL stylesheet = getClass().getResource("/styles/app.css");

        if (stylesheet == null) {
            System.out.println("Stylesheet dialog non trovato: /styles/app.css");
            return;
        }

        dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
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

    private VBox buildTopBar() {
        Label searchModeLabel = new Label("Ricerca:");

        searchModeCombo = new ComboBox<>();
        searchModeCombo.getItems().addAll(
                "Testo libero",
                "ORCID",
                "IRIS ID",
                "Codice fiscale"
        );

        // Avvio diretto in modalità IRIS ID
        searchModeCombo.getSelectionModel().select("IRIS ID");
        searchModeCombo.setPrefWidth(160);

        searchInputField = new TextField();
        searchInputField.setPromptText("Es. rp00418 / ORCID / codice fiscale / nome docente");
        searchInputField.setPrefWidth(340);
        searchInputField.setOnAction(event -> searchProfessor());

        Button searchProfessorButton = new Button("Cerca professore");
        searchProfessorButton.getStyleClass().add("primary-button");

        Button professorLookupButton = new Button("Rubrica CF");
        professorLookupButton.getStyleClass().add("success-button");

        Button settingsButton = new Button("⚙");
        settingsButton.getStyleClass().add("secondary-button");
        settingsButton.setMinWidth(44);
        settingsButton.setPrefWidth(44);
        settingsButton.setMaxWidth(44);

        searchProfessorButton.setOnAction(event -> searchProfessor());
        professorLookupButton.setOnAction(event -> showProfessorLookupDialog());
        settingsButton.setOnAction(event -> showConnectionSettingsDialog());

        Region settingsSpacer = new Region();
        HBox.setHgrow(settingsSpacer, Priority.ALWAYS);

        HBox controlsBar = new HBox(
                10,
                searchModeLabel,
                searchModeCombo,
                searchInputField,
                searchProfessorButton,
                professorLookupButton,
                settingsSpacer,
                settingsButton
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
                nameLabel,
                professorNameValue,
                affiliationLabel,
                professorAffiliationValue,
                identifiersLabel,
                professorIdentifiersArea
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
        HBox.setHgrow(publicationFilterField, Priority.ALWAYS);

        Button refreshIrisButton = new Button("Refresh IRIS");
        refreshIrisButton.getStyleClass().add("primary-button");
        refreshIrisButton.setOnAction(event -> refreshProfessorPublications());

        HBox publicationSearchBar = new HBox(10, publicationFilterField, refreshIrisButton);
        publicationSearchBar.setAlignment(Pos.CENTER_LEFT);

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
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        return;
                    }

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

        VBox publicationsPane = new VBox(8, sectionTitle, publicationSearchBar, publicationsTable);
        publicationsPane.getStyleClass().add("panel");
        publicationsPane.setPadding(new Insets(10));
        VBox.setVgrow(publicationsTable, Priority.ALWAYS);

        return publicationsPane;
    }

    private VBox buildDetailsPane() {
        Label publicationTitle = new Label("Dettaglio pubblicazione");
        publicationTitle.getStyleClass().add("section-title");

        Button refreshSelectedCitationButton = new Button("Refresh Scopus/Scholar");
        refreshSelectedCitationButton.getStyleClass().add("primary-button");
        refreshSelectedCitationButton.setOnAction(event -> refreshSelectedPublicationCitationData());

        Region publicationHeaderSpacer = new Region();
        HBox.setHgrow(publicationHeaderSpacer, Priority.ALWAYS);

        HBox publicationHeader = new HBox(
                10,
                publicationTitle,
                publicationHeaderSpacer,
                refreshSelectedCitationButton
        );
        publicationHeader.setAlignment(Pos.CENTER_LEFT);

        publicationDetailsArea = new TextArea();
        publicationDetailsArea.setEditable(false);
        publicationDetailsArea.setWrapText(true);
        publicationDetailsArea.setPrefRowCount(12);

        Label citationTitle = new Label("Numero Citazioni e Documenti Citanti");
        citationTitle.getStyleClass().add("section-title");

        Button scholarCitingDocumentsButton = new Button("Documenti citanti Scholar");
        scholarCitingDocumentsButton.getStyleClass().add("secondary-button");
        scholarCitingDocumentsButton.setOnAction(event -> showCitingDocumentsDialog(SourceType.SCHOLAR));

        Button scopusCitingDocumentsButton = new Button("Documenti citanti Scopus");
        scopusCitingDocumentsButton.getStyleClass().add("secondary-button");
        scopusCitingDocumentsButton.setOnAction(event -> showCitingDocumentsDialog(SourceType.SCOPUS));

        HBox citingDocumentsButtonsBar = new HBox(
                10,
                scholarCitingDocumentsButton,
                scopusCitingDocumentsButton
        );
        citingDocumentsButtonsBar.setAlignment(Pos.CENTER_LEFT);

        citationDetailsArea = new TextArea();
        citationDetailsArea.setEditable(false);
        citationDetailsArea.setWrapText(true);
        citationDetailsArea.setPrefRowCount(16);

        VBox detailsPane = new VBox(
                8,
                publicationHeader,
                publicationDetailsArea,
                citationTitle,
                citingDocumentsButtonsBar,
                citationDetailsArea
        );
        detailsPane.getStyleClass().add("panel");
        detailsPane.setPadding(new Insets(10));

        VBox.setVgrow(publicationDetailsArea, Priority.ALWAYS);
        VBox.setVgrow(citationDetailsArea, Priority.ALWAYS);

        return detailsPane;
    }

    private void showConnectionSettingsDialog() {
        ConnectionSettings currentSettings = loadConnectionSettingsForDialog();

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Impostazioni API key");
        dialog.setResizable(true);
        applyDialogIcon(dialog);
        applyDialogStylesheet(dialog);

        ButtonType saveButtonType = new ButtonType("Salva", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        Label introLabel = new Label(
                "Inserisci qui le credenziali locali dell'applicazione. "
                        + "Le API key vengono salvate solo sul tuo PC e non devono essere caricate su Git."
        );
        introLabel.setWrapText(true);

        Label pathLabel = new Label("File locale: " + localSettingsRepository.getSettingsPath());
        pathLabel.setWrapText(true);

        Label irisSectionLabel = new Label("IRIS / CINECA");
        irisSectionLabel.getStyleClass().add("section-title");

        TextField irisUsernameField = new TextField();
        irisUsernameField.setPromptText("Username IRIS REST");
        irisUsernameField.setText(currentSettings.irisRestUsername());

        PasswordField irisPasswordField = new PasswordField();
        irisPasswordField.setPromptText("Password IRIS REST");
        irisPasswordField.setText(currentSettings.irisRestPassword());

        Label scopusSectionLabel = new Label("Scopus / Elsevier");
        scopusSectionLabel.getStyleClass().add("section-title");

        PasswordField scopusApiKeyField = new PasswordField();
        scopusApiKeyField.setPromptText("SCOPUS_API_KEY");
        scopusApiKeyField.setText(currentSettings.scopusApiKey());

        PasswordField scopusInstTokenField = new PasswordField();
        scopusInstTokenField.setPromptText("SCOPUS_INST_TOKEN opzionale");
        scopusInstTokenField.setText(currentSettings.scopusInstToken());

        Label scholarSectionLabel = new Label("Google Scholar tramite SerpApi");
        scholarSectionLabel.getStyleClass().add("section-title");

        PasswordField serpApiKeyField = new PasswordField();
        serpApiKeyField.setPromptText("SERPAPI_API_KEY");
        serpApiKeyField.setText(currentSettings.serpApiApiKey());

        Label restartLabel = new Label(
                "Nota: dopo il salvataggio riavvia l'applicazione per applicare le nuove API key ai connector."
        );
        restartLabel.setWrapText(true);

        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(8);
        formGrid.setPadding(new Insets(10, 0, 0, 0));

        int row = 0;

        formGrid.add(irisSectionLabel, 0, row++, 2, 1);
        formGrid.add(new Label("Username"), 0, row);
        formGrid.add(irisUsernameField, 1, row++);
        formGrid.add(new Label("Password"), 0, row);
        formGrid.add(irisPasswordField, 1, row++);

        formGrid.add(scopusSectionLabel, 0, row++, 2, 1);
        formGrid.add(new Label("API key"), 0, row);
        formGrid.add(scopusApiKeyField, 1, row++);
        formGrid.add(new Label("Institutional token"), 0, row);
        formGrid.add(scopusInstTokenField, 1, row++);

        formGrid.add(scholarSectionLabel, 0, row++, 2, 1);
        formGrid.add(new Label("SerpApi API key"), 0, row);
        formGrid.add(serpApiKeyField, 1, row++);

        GridPane.setHgrow(irisUsernameField, Priority.ALWAYS);
        GridPane.setHgrow(irisPasswordField, Priority.ALWAYS);
        GridPane.setHgrow(scopusApiKeyField, Priority.ALWAYS);
        GridPane.setHgrow(scopusInstTokenField, Priority.ALWAYS);
        GridPane.setHgrow(serpApiKeyField, Priority.ALWAYS);

        VBox content = new VBox(
                10,
                introLabel,
                pathLabel,
                formGrid,
                restartLabel
        );
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(10));
        content.setPrefWidth(680);

        dialog.getDialogPane().setContent(content);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String validationError = validateConnectionSettingsInput(
                    irisUsernameField.getText(),
                    irisPasswordField.getText(),
                    scopusApiKeyField.getText(),
                    scopusInstTokenField.getText(),
                    serpApiKeyField.getText()
            );

            if (validationError != null) {
                showErrorAlert("Impostazioni non valide", validationError);
                event.consume();
                return;
            }

            ConnectionSettings updatedSettings = buildUpdatedConnectionSettings(
                    currentSettings,
                    irisUsernameField.getText(),
                    irisPasswordField.getText(),
                    scopusApiKeyField.getText(),
                    scopusInstTokenField.getText(),
                    serpApiKeyField.getText()
            );

            try {
                localSettingsRepository.save(updatedSettings);

                updateStatus("Impostazioni salvate. Riavvia l'applicazione per applicare le nuove API key.");

                showInfoAlert(
                        "Impostazioni salvate",
                        "Le impostazioni sono state salvate correttamente.\n\n"
                                + "File locale:\n"
                                + localSettingsRepository.getSettingsPath()
                                + "\n\nRiavvia l'applicazione per applicare le nuove API key."
                );
            } catch (IOException e) {
                showErrorAlert(
                        "Errore salvataggio impostazioni",
                        "Non è stato possibile salvare il file settings.properties."
                );
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private ConnectionSettings loadConnectionSettingsForDialog() {
        try {
            return localSettingsRepository.load().normalized();
        } catch (IOException e) {
            updateStatus("Impossibile leggere settings.properties. Uso valori vuoti/default.");
            return ConnectionSettings.empty();
        }
    }

    private ConnectionSettings buildUpdatedConnectionSettings(
            ConnectionSettings currentSettings,
            String irisUsername,
            String irisPassword,
            String scopusApiKey,
            String scopusInstToken,
            String serpApiApiKey
    ) {
        ConnectionSettings safeCurrentSettings = currentSettings != null
                ? currentSettings.normalized()
                : ConnectionSettings.empty();

        return new ConnectionSettings(
                safeCurrentSettings.irisRestBaseUrl(),
                safeCurrentSettings.irisRestPathIr(),
                safeCurrentSettings.irisRestPathRm(),
                irisUsername,
                irisPassword,
                safeCurrentSettings.irisRestTimeoutSeconds(),

                safeCurrentSettings.scopusBaseUrl(),
                scopusApiKey,
                scopusInstToken,
                safeCurrentSettings.scopusTimeoutSeconds(),

                safeCurrentSettings.serpApiBaseUrl(),
                serpApiApiKey,
                safeCurrentSettings.serpApiTimeoutSeconds()
        ).normalized();
    }

    private String validateConnectionSettingsInput(
            String irisUsername,
            String irisPassword,
            String scopusApiKey,
            String scopusInstToken,
            String serpApiApiKey
    ) {
        boolean irisUsernameConfigured = hasText(irisUsername);
        boolean irisPasswordConfigured = hasText(irisPassword);

        if (irisUsernameConfigured != irisPasswordConfigured) {
            return "Per IRIS devi inserire sia username sia password, oppure lasciare entrambi vuoti.";
        }

        if (containsWhitespace(scopusApiKey)) {
            return "La SCOPUS_API_KEY non deve contenere spazi.";
        }

        if (containsWhitespace(scopusInstToken)) {
            return "Lo SCOPUS_INST_TOKEN non deve contenere spazi.";
        }

        if (containsWhitespace(serpApiApiKey)) {
            return "La SERPAPI_API_KEY non deve contenere spazi.";
        }

        return null;
    }

    private boolean containsWhitespace(String value) {
        if (!hasText(value)) {
            return false;
        }

        return value.trim().matches(".*\\s+.*");
    }

    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "");
        applyDialogIcon(alert);
        applyDialogStylesheet(alert);
        alert.showAndWait();
    }

    private void showProfessorLookupDialog() {
        AtomicReference<List<ProfessorLookupEntry>> allEntries =
                new AtomicReference<>(professorLookupRepository.findAll());

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Rubrica CF");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        applyDialogIcon(dialog);
        applyDialogStylesheet(dialog);

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

        Button helpButton = new Button("? Aiuto");
        helpButton.getStyleClass().add("cf-help-bib-style-button");

        HBox lookupToolbar = new HBox(10, filterField, useButton, addButton);
        lookupToolbar.setAlignment(Pos.CENTER_LEFT);

        HBox lookupBottomBar = new HBox(helpButton);
        lookupBottomBar.getStyleClass().add("lookup-bottom-bar");
        lookupBottomBar.setAlignment(Pos.CENTER_LEFT);

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
                    + selectedEntry.nome()
                    + " "
                    + selectedEntry.cognome()
                    + ".");

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

        VBox content = new VBox(10, infoLabel, lookupToolbar, lookupTable, lookupBottomBar);
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
        applyDialogStylesheet(dialog);

        TextArea helpArea = new TextArea();
        helpArea.setEditable(false);
        helpArea.setWrapText(true);
        helpArea.setPrefSize(640, 420);

        helpArea.setText("""
                Rubrica CF - Guida rapida

                La Rubrica CF è una rubrica locale e personale.
                Per motivi di privacy, l'applicazione viene distribuita con una rubrica vuota.

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

                Nota importante: I DATI RESTANO LOCALI
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
        applyDialogStylesheet(alert);

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
        applyDialogStylesheet(dialog);

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

    private void refreshSelectedPublicationCitationData() {
        Publication selectedPublication = publicationsTable.getSelectionModel().getSelectedItem();

        if (selectedPublication == null) {
            updateStatus("Seleziona una pubblicazione prima di aggiornare Scopus/Scholar.");
            return;
        }

        CitationSummary updatedSummary = citationService.refreshCitationSummary(selectedPublication);
        List<CitingDocument> updatedDocuments = citationService.refreshCitingDocuments(selectedPublication);

        showCitationDetails(selectedPublication);

        String scopusCount = updatedSummary.scopusCitationCount() != null
                ? updatedSummary.scopusCitationCount().toString()
                : "N/D";

        String scholarCount = updatedSummary.scholarCitationCount() != null
                ? updatedSummary.scholarCitationCount().toString()
                : "N/D";

        String scopusEid = hasText(updatedSummary.scopusEid())
                ? updatedSummary.scopusEid()
                : "N/D";

        String partialDataMessage = hasText(updatedSummary.scopusCitingDocumentsNote())
                ? " Stato documenti citanti Scopus: PARTIAL_DATA."
                : "";

        updateStatus("Citazioni Scopus/Scholar aggiornate per la pubblicazione selezionata. "
                + "Scopus: "
                + scopusCount
                + ", Scholar: "
                + scholarCount
                + ", EID Scopus: "
                + scopusEid
                + ", documenti citanti: "
                + updatedDocuments.size()
                + "."
                + partialDataMessage);
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
        applyDialogStylesheet(dialog);

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
        applyDialogStylesheet(alert);
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

        long scholarDocumentsCount = citingDocuments.stream()
                .filter(document -> document.sourceType() == SourceType.SCHOLAR)
                .count();

        long scopusDocumentsCount = citingDocuments.stream()
                .filter(document -> document.sourceType() == SourceType.SCOPUS)
                .count();

        StringBuilder builder = new StringBuilder();

        builder.append("Citazioni Scopus: ")
                .append(summary.scopusCitationCount() != null ? summary.scopusCitationCount() : "N/D")
                .append("\n");

        builder.append("EID Scopus: ")
                .append(hasText(summary.scopusEid()) ? summary.scopusEid() : "N/D")
                .append("\n");

        builder.append("Citazioni Scholar: ")
                .append(summary.scholarCitationCount() != null ? summary.scholarCitationCount() : "N/D")
                .append("\n");

        builder.append("Totale aggregato: ")
                .append(summary.totalCitationCount() != null ? summary.totalCitationCount() : "N/D")
                .append("\n\n");

        builder.append("Documenti citanti Scholar in cache: ")
                .append(scholarDocumentsCount)
                .append("\n");

        builder.append("Documenti citanti Scopus in cache: ")
                .append(scopusDocumentsCount)
                .append("\n\n");

        if (hasText(summary.scopusCitingDocumentsNote())) {
            builder.append("Stato documenti citanti Scopus: PARTIAL_DATA\n");
            builder.append("Nota Scopus: ")
                    .append(summary.scopusCitingDocumentsNote())
                    .append("\n\n");
        }

        builder.append("Usa i pulsanti \"Documenti citanti Scholar\" e \"Documenti citanti Scopus\" ")
                .append("per aprire l'elenco tabellare dei documenti citanti della pubblicazione selezionata.");

        citationDetailsArea.setText(builder.toString());
    }

    private void showCitingDocumentsDialog(SourceType sourceType) {
        Publication selectedPublication = publicationsTable.getSelectionModel().getSelectedItem();

        if (selectedPublication == null) {
            updateStatus("Seleziona una pubblicazione prima di aprire i documenti citanti.");
            return;
        }

        List<CitingDocument> sourceDocuments = sortCitingDocumentsByYearDesc(
                citationService.getCachedCitingDocuments(selectedPublication)
                        .stream()
                        .filter(document -> document.sourceType() == sourceType)
                        .toList()
        );

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Documenti citanti " + sourceDisplayName(sourceType));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        applyDialogIcon(dialog);
        applyDialogStylesheet(dialog);

        Label infoLabel = new Label(
                "Pubblicazione selezionata: "
                        + selectedPublication.title()
                        + "\nDocumenti citanti "
                        + sourceDisplayName(sourceType)
                        + " trovati in cache: "
                        + sourceDocuments.size()
        );
        infoLabel.setWrapText(true);

        TextField filterField = new TextField();
        filterField.setPromptText("Filtra per titolo, autore, anno, DOI o URL");
        filterField.setPrefWidth(520);
        HBox.setHgrow(filterField, Priority.ALWAYS);

        ObservableList<CitingDocument> filteredDocuments =
                FXCollections.observableArrayList(sourceDocuments);

        TableView<CitingDocument> table = new TableView<>();
        table.setItems(filteredDocuments);
        table.setPrefSize(1180, 520);

        TableColumn<CitingDocument, String> titleColumn = new TableColumn<>("Titolo");
        titleColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().title() != null ? cellData.getValue().title() : "N/D"
                )
        );
        titleColumn.setPrefWidth(330);

        TableColumn<CitingDocument, String> authorsColumn = new TableColumn<>("Autori");
        authorsColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(formatAuthors(cellData.getValue().authors()))
        );
        authorsColumn.setPrefWidth(230);

        TableColumn<CitingDocument, Integer> yearColumn = new TableColumn<>("Anno");
        yearColumn.setCellValueFactory(cellData ->
                new ReadOnlyObjectWrapper<>(cellData.getValue().year())
        );
        yearColumn.setPrefWidth(70);

        TableColumn<CitingDocument, Void> bibtexColumn = new TableColumn<>("BibTeX");
        bibtexColumn.setPrefWidth(85);
        bibtexColumn.setCellFactory(param -> new TableCell<>() {
            private final Button bibtexButton = new Button(".bib");

            {
                bibtexButton.getStyleClass().add("success-button");
                bibtexButton.setOnAction(event -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        return;
                    }

                    CitingDocument document = getTableView().getItems().get(getIndex());
                    showBibtexForCitingDocument(document);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                setGraphic(bibtexButton);
            }
        });

        TableColumn<CitingDocument, Void> urlColumn = new TableColumn<>("URL");
        urlColumn.setPrefWidth(260);
        urlColumn.setCellFactory(param -> new TableCell<>() {
            private final Hyperlink hyperlink = new Hyperlink();

            {
                hyperlink.setOnAction(event -> {
                    if (getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                        return;
                    }

                    CitingDocument document = getTableView().getItems().get(getIndex());
                    openExternalUrl(document.sourceUrl());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }

                CitingDocument document = getTableView().getItems().get(getIndex());
                String url = document.sourceUrl();

                if (!hasText(url)) {
                    setGraphic(new Label("N/D"));
                    return;
                }

                hyperlink.setText(abbreviateForTable(url, 42));
                setGraphic(hyperlink);
            }
        });

        TableColumn<CitingDocument, String> doiColumn = new TableColumn<>("DOI");
        doiColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(
                        cellData.getValue().doi() != null ? cellData.getValue().doi() : "N/D"
                )
        );
        doiColumn.setPrefWidth(170);

        TableColumn<CitingDocument, String> sourceColumn = new TableColumn<>("Sorgente");
        sourceColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(String.valueOf(cellData.getValue().sourceType()))
        );
        sourceColumn.setPrefWidth(90);

        TableColumn<CitingDocument, String> statusColumn = new TableColumn<>("Stato");
        statusColumn.setCellValueFactory(cellData ->
                new ReadOnlyStringWrapper(String.valueOf(cellData.getValue().recordStatus()))
        );
        statusColumn.setPrefWidth(110);

        table.getColumns().add(titleColumn);
        table.getColumns().add(authorsColumn);
        table.getColumns().add(yearColumn);
        table.getColumns().add(bibtexColumn);
        table.getColumns().add(urlColumn);
        table.getColumns().add(doiColumn);
        table.getColumns().add(sourceColumn);
        table.getColumns().add(statusColumn);

        filterField.textProperty().addListener((obs, oldValue, newValue) ->
                refreshCitingDocumentsTable(filteredDocuments, sourceDocuments, newValue)
        );

        Label emptyInfoLabel = new Label(buildEmptyCitingDocumentsMessage(sourceType));
        emptyInfoLabel.setWrapText(true);
        emptyInfoLabel.setVisible(sourceDocuments.isEmpty());
        emptyInfoLabel.setManaged(sourceDocuments.isEmpty());

        HBox filterBar = new HBox(10, filterField);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, infoLabel, filterBar, table, emptyInfoLabel);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private void refreshCitingDocumentsTable(
            ObservableList<CitingDocument> filteredDocuments,
            List<CitingDocument> allDocuments,
            String query
    ) {
        String normalizedQuery = query != null ? query.trim().toLowerCase() : "";

        if (normalizedQuery.isBlank()) {
            filteredDocuments.setAll(sortCitingDocumentsByYearDesc(allDocuments));
            return;
        }

        filteredDocuments.setAll(
                sortCitingDocumentsByYearDesc(
                        allDocuments.stream()
                                .filter(document -> citingDocumentMatchesFilter(document, normalizedQuery))
                                .toList()
                )
        );
    }

    private boolean citingDocumentMatchesFilter(CitingDocument document, String query) {
        if (document == null) {
            return false;
        }

        String title = document.title() != null ? document.title().toLowerCase() : "";
        String authors = document.authors() != null
                ? String.join(" ", document.authors()).toLowerCase()
                : "";
        String year = document.year() != null ? String.valueOf(document.year()) : "";
        String doi = document.doi() != null ? document.doi().toLowerCase() : "";
        String url = document.sourceUrl() != null ? document.sourceUrl().toLowerCase() : "";

        return title.contains(query)
                || authors.contains(query)
                || year.contains(query)
                || doi.contains(query)
                || url.contains(query);
    }

    private List<CitingDocument> sortCitingDocumentsByYearDesc(List<CitingDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        return documents.stream()
                .sorted(
                        Comparator.comparing(
                                CitingDocument::year,
                                Comparator.nullsLast(Comparator.reverseOrder())
                        ).thenComparing(
                                CitingDocument::title,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                        )
                )
                .toList();
    }

    private String formatAuthors(List<String> authors) {
        if (authors == null || authors.isEmpty()) {
            return "N/D";
        }

        return String.join(", ", authors);
    }

    private String sourceDisplayName(SourceType sourceType) {
        if (sourceType == SourceType.SCHOLAR) {
            return "Scholar";
        }

        if (sourceType == SourceType.SCOPUS) {
            return "Scopus";
        }

        return String.valueOf(sourceType);
    }

    private String buildEmptyCitingDocumentsMessage(SourceType sourceType) {
        if (sourceType == SourceType.SCOPUS) {
            return "Nessun documento citante Scopus disponibile in cache.\n"
                    + "Il citation count Scopus può essere disponibile, ma l'elenco dei documenti citanti "
                    + "richiede permessi API aggiuntivi oppure test da rete Ateneo/VPN.";
        }

        if (sourceType == SourceType.SCHOLAR) {
            return "Nessun documento citante Scholar disponibile in cache.\n"
                    + "Premi prima \"Refresh Scopus/Scholar\". "
                    + "Se Scholar/SerpApi espone cited_by/cites_id, i documenti citanti verranno mostrati qui.";
        }

        return "Nessun documento citante disponibile in cache.";
    }

    private void showBibtexForCitingDocument(CitingDocument document) {
        if (document == null) {
            updateStatus("Seleziona prima un documento citante.");
            return;
        }

        Optional<BibtexEntry> result = generateBibtexForCitingDocument(document);

        if (result.isEmpty()) {
            updateStatus("Impossibile generare il BibTeX per il documento citante.");
            return;
        }

        BibtexEntry bibtexEntry = result.get();
        updateStatus("BibTeX generato per documento citante da sorgente: "
                + bibtexEntry.sourceType()
                + ".");

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("BibTeX documento citante - " + safeDisplayText(document.title()));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        applyDialogIcon(dialog);
        applyDialogStylesheet(dialog);

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

    private Optional<BibtexEntry> generateBibtexForCitingDocument(CitingDocument document) {
        if (document == null || !hasText(document.title())) {
            return Optional.empty();
        }

        String citationKey = buildCitingDocumentCitationKey(document);
        String entryType = hasText(document.doi()) ? "article" : "misc";

        StringBuilder builder = new StringBuilder();

        builder.append("@")
                .append(entryType)
                .append("{")
                .append(citationKey)
                .append(",\n");

        builder.append("  title = {")
                .append(escapeBibtexValue(document.title()))
                .append("}");

        if (document.authors() != null && !document.authors().isEmpty()) {
            builder.append(",\n");
            builder.append("  author = {")
                    .append(escapeBibtexValue(String.join(" and ", document.authors())))
                    .append("}");
        }

        if (document.year() != null) {
            builder.append(",\n");
            builder.append("  year = {")
                    .append(document.year())
                    .append("}");
        }

        if (hasText(document.doi())) {
            builder.append(",\n");
            builder.append("  doi = {")
                    .append(escapeBibtexValue(document.doi()))
                    .append("}");
        }

        if (hasText(document.sourceUrl())) {
            builder.append(",\n");
            builder.append("  url = {")
                    .append(escapeBibtexValue(document.sourceUrl()))
                    .append("}");
        }

        builder.append(",\n");
        builder.append("  note = {Citing document retrieved from ")
                .append(document.sourceType())
                .append("}");

        builder.append("\n}");

        return Optional.of(new BibtexEntry(
                citationKey,
                entryType,
                builder.toString(),
                document.sourceType(),
                document.recordStatus()
        ));
    }

    private String buildCitingDocumentCitationKey(CitingDocument document) {
        String firstAuthor = "citing";

        if (document.authors() != null && !document.authors().isEmpty()) {
            firstAuthor = document.authors().get(0);
        }

        String year = document.year() != null ? document.year().toString() : "nd";
        String titlePart = document.title() != null ? document.title() : "document";

        String normalizedAuthor = normalizeCitationKeyPart(firstAuthor);
        String normalizedTitle = normalizeCitationKeyPart(firstWords(titlePart, 4));

        String citationKey = normalizedAuthor + year + normalizedTitle;

        if (citationKey.isBlank()) {
            return "citingDocument";
        }

        return citationKey;
    }

    private String firstWords(String value, int maxWords) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String[] words = value.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < words.length && i < maxWords; i++) {
            builder.append(words[i]);
        }

        return builder.toString();
    }

    private String normalizeCitationKeyPart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "")
                .trim();
    }

    private String escapeBibtexValue(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

    private void openExternalUrl(String url) {
        if (!hasText(url)) {
            updateStatus("URL non disponibile per il documento citante selezionato.");
            return;
        }

        try {
            getHostServices().showDocument(url);
            updateStatus("URL documento citante aperto nel browser.");
        } catch (Exception e) {
            updateStatus("Impossibile aprire l'URL del documento citante.");
            showErrorAlert(
                    "Errore apertura URL",
                    "Non è stato possibile aprire il link:\n" + url
            );
        }
    }

    private String abbreviateForTable(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim();

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String safeDisplayText(String value) {
        if (!hasText(value)) {
            return "N/D";
        }

        return abbreviateForTable(value, 80);
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
        citationDetailsArea.setText("Seleziona una pubblicazione e premi \"Refresh Scopus/Scholar\" per vedere il numero di citazioni.");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private void updateStatus(String statusText) {
        statusLabel.setText(statusText);
    }

    public static void main(String[] args) {
        launch(args);
    }
}