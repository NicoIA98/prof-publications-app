package com.iadanza.profpublicationsapp.ui.dialog;

import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import javafx.application.HostServices;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog tabellare per i documenti citanti di una pubblicazione.
 *
 * Responsabilità:
 * - mostrare documenti citanti Scholar/Scopus;
 * - ordinare per anno decrescente;
 * - filtrare per titolo, autore, anno, DOI o URL;
 * - aprire URL esterni;
 * - delegare alla UI principale la generazione BibTeX.
 */
public class CitingDocumentsDialog {

    private final SourceType sourceType;
    private final Publication selectedPublication;
    private final List<CitingDocument> sourceDocuments;
    private final HostServices hostServices;
    private final Consumer<String> statusConsumer;
    private final Consumer<CitingDocument> bibtexConsumer;

    public CitingDocumentsDialog(
            SourceType sourceType,
            Publication selectedPublication,
            List<CitingDocument> sourceDocuments,
            HostServices hostServices,
            Consumer<String> statusConsumer,
            Consumer<CitingDocument> bibtexConsumer
    ) {
        this.sourceType = sourceType;
        this.selectedPublication = selectedPublication;
        this.sourceDocuments = sortCitingDocumentsByYearDesc(
                sourceDocuments != null ? sourceDocuments : List.of()
        );
        this.hostServices = hostServices;
        this.statusConsumer = statusConsumer != null ? statusConsumer : ignored -> { };
        this.bibtexConsumer = bibtexConsumer != null ? bibtexConsumer : ignored -> { };
    }

    public void showAndWait() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Documenti citanti " + sourceDisplayName(sourceType));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        DialogSupport.applyDialogIcon(dialog);
        DialogSupport.applyDialogStylesheet(dialog);

        Label infoLabel = new Label(
                "Pubblicazione selezionata: "
                        + safeText(selectedPublication != null ? selectedPublication.title() : null)
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
                    bibtexConsumer.accept(document);
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

    private void openExternalUrl(String url) {
        if (!hasText(url)) {
            statusConsumer.accept("URL non disponibile per il documento citante selezionato.");
            return;
        }

        if (hostServices == null) {
            statusConsumer.accept("Servizio apertura browser non disponibile.");
            DialogSupport.showErrorAlert(
                    "Errore apertura URL",
                    "Non è stato possibile aprire il link:\n" + url
            );
            return;
        }

        try {
            hostServices.showDocument(url);
            statusConsumer.accept("URL documento citante aperto nel browser.");
        } catch (Exception e) {
            statusConsumer.accept("Impossibile aprire l'URL del documento citante.");
            DialogSupport.showErrorAlert(
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

    private String safeText(String value) {
        if (!hasText(value)) {
            return "N/D";
        }

        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}