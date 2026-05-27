package com.iadanza.profpublicationsapp;

import com.iadanza.profpublicationsapp.application.service.BibtexService;
import com.iadanza.profpublicationsapp.application.service.CitationService;
import com.iadanza.profpublicationsapp.application.service.ProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.bootstrap.AppBootstrap;
import com.iadanza.profpublicationsapp.bootstrap.AppServices;
import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.ProfessorLookupEntry;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.config.ConnectionSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.LocalSettingsRepository;
import com.iadanza.profpublicationsapp.infrastructure.lookup.ProfessorLookupRepository;
import com.iadanza.profpublicationsapp.ui.component.PublicationsTableFactory;
import com.iadanza.profpublicationsapp.ui.dialog.BibtexDialog;
import com.iadanza.profpublicationsapp.ui.dialog.CitingDocumentsDialog;
import com.iadanza.profpublicationsapp.ui.dialog.ConnectionSettingsDialog;
import com.iadanza.profpublicationsapp.ui.dialog.ProfessorLookupDialog;
import com.iadanza.profpublicationsapp.ui.dialog.StartupSettingsWarningDialog;
import com.iadanza.profpublicationsapp.ui.formatter.CitationDetailsFormatter;
import com.iadanza.profpublicationsapp.ui.formatter.PublicationDetailsFormatter;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Classe principale dell'applicazione JavaFX.
 *
 * Stato attuale:
 * - bootstrap infrastrutturale spostato in AppBootstrap;
 * - servizi applicativi ricevuti tramite AppServices;
 * - gestione settings locali tramite LocalSettingsRepository;
 * - dialog Impostazioni estratta in ui.dialog.ConnectionSettingsDialog;
 * - dialog primo avvio estratta in ui.dialog.StartupSettingsWarningDialog;
 * - dialog Rubrica CF estratta in ui.dialog.ProfessorLookupDialog;
 * - dialog Documenti Citanti estratta in ui.dialog.CitingDocumentsDialog;
 * - dialog BibTeX estratta in ui.dialog.BibtexDialog;
 * - tabella Pubblicazioni IRIS estratta in ui.component.PublicationsTableFactory;
 * - formatter dettagli pubblicazione/citazioni estratti in ui.formatter;
 * - questa classe resta coordinatore UI principale.
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

        Platform.runLater(this::showStartupSettingsWarningIfNeeded);
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

    private VBox buildTopBar() {
        Label searchModeLabel = new Label("Ricerca:");

        searchModeCombo = new ComboBox<>();
        searchModeCombo.getItems().addAll(
                "Testo libero",
                "ORCID",
                "IRIS ID",
                "Codice fiscale"
        );

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
        publicationFilterField.setPromptText("Filtra pubblicazioni per titolo, anno, autore o DOI");
        publicationFilterField.getStyleClass().add("publication-filter");
        publicationFilterField.textProperty().addListener((obs, oldValue, newValue) -> applyPublicationFilter());
        HBox.setHgrow(publicationFilterField, Priority.ALWAYS);

        Button refreshIrisButton = new Button("Refresh IRIS");
        refreshIrisButton.getStyleClass().add("primary-button");
        refreshIrisButton.setOnAction(event -> refreshProfessorPublications());

        HBox publicationSearchBar = new HBox(10, publicationFilterField, refreshIrisButton);
        publicationSearchBar.setAlignment(Pos.CENTER_LEFT);

        publicationsTable = PublicationsTableFactory.create(
                publicationItems,
                this::handlePublicationSelected,
                this::handlePublicationSelectionCleared,
                this::showBibtexForPublication
        );

        VBox publicationsPane = new VBox(8, sectionTitle, publicationSearchBar, publicationsTable);
        publicationsPane.getStyleClass().add("panel");
        publicationsPane.setPadding(new Insets(10));
        VBox.setVgrow(publicationsTable, Priority.ALWAYS);

        return publicationsPane;
    }

    private void handlePublicationSelected(Publication publication) {
        if (publication == null) {
            handlePublicationSelectionCleared();
            return;
        }

        showPublicationDetails(publication);
        showCitationDetails(publication);
    }

    private void handlePublicationSelectionCleared() {
        resetPublicationDetails();
        resetCitationDetails();
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

    private void showStartupSettingsWarningIfNeeded() {
        ConnectionSettings settings;

        try {
            settings = localSettingsRepository.load().normalized();
        } catch (IOException e) {
            updateStatus("Impossibile leggere settings.properties. Configurazione iniziale richiesta.");
            settings = ConnectionSettings.empty();
        }

        StartupSettingsWarningDialog startupDialog = new StartupSettingsWarningDialog(
                settings,
                localSettingsRepository.getSettingsPath()
        );

        if (!startupDialog.shouldShow()) {
            return;
        }

        boolean openSettings = startupDialog.showAndWaitForOpenSettings();

        if (openSettings) {
            showConnectionSettingsDialog();
        } else {
            updateStatus("Modalità limitata: alcune sorgenti potrebbero non essere disponibili senza credenziali/API key.");
        }
    }

    private void showConnectionSettingsDialog() {
        new ConnectionSettingsDialog(
                localSettingsRepository,
                this::updateStatus
        ).showAndWait();
    }

    private void showProfessorLookupDialog() {
        new ProfessorLookupDialog(
                professorLookupRepository,
                this::updateStatus,
                this::useProfessorLookupEntryForSearch
        ).showAndWait();
    }

    private void useProfessorLookupEntryForSearch(ProfessorLookupEntry selectedEntry) {
        if (selectedEntry == null) {
            updateStatus("Seleziona una riga dalla rubrica CF.");
            return;
        }

        searchModeCombo.getSelectionModel().select("Codice fiscale");
        searchInputField.setText(selectedEntry.codiceFiscale());

        updateStatus("Codice fiscale selezionato dalla rubrica: "
                + selectedEntry.nome()
                + " "
                + selectedEntry.cognome()
                + ".");

        searchProfessor();
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
        new BibtexDialog(
                bibtexService,
                getOwnerWindow(),
                this::updateStatus
        ).showForPublication(publication);
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
        publicationDetailsArea.setText(
                PublicationDetailsFormatter.format(publication)
        );
    }

    private void showCitationDetails(Publication publication) {
        CitationSummary summary = citationService.getCachedCitationSummary(publication);
        List<CitingDocument> citingDocuments = citationService.getCachedCitingDocuments(publication);

        citationDetailsArea.setText(
                CitationDetailsFormatter.format(summary, citingDocuments)
        );
    }

    private void showCitingDocumentsDialog(SourceType sourceType) {
        Publication selectedPublication = publicationsTable.getSelectionModel().getSelectedItem();

        if (selectedPublication == null) {
            updateStatus("Seleziona una pubblicazione prima di aprire i documenti citanti.");
            return;
        }

        List<CitingDocument> sourceDocuments = citationService.getCachedCitingDocuments(selectedPublication)
                .stream()
                .filter(document -> document.sourceType() == sourceType)
                .toList();

        new CitingDocumentsDialog(
                sourceType,
                selectedPublication,
                sourceDocuments,
                getHostServices(),
                this::updateStatus,
                this::showBibtexForCitingDocument
        ).showAndWait();
    }

    private void showBibtexForCitingDocument(CitingDocument document) {
        new BibtexDialog(
                bibtexService,
                getOwnerWindow(),
                this::updateStatus
        ).showForCitingDocument(document);
    }

    private Window getOwnerWindow() {
        if (publicationsTable == null || publicationsTable.getScene() == null) {
            return null;
        }

        return publicationsTable.getScene().getWindow();
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