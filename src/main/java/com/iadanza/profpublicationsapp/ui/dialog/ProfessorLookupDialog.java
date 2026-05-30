package com.iadanza.profpublicationsapp.ui.dialog;

import com.iadanza.profpublicationsapp.domain.model.ProfessorLookupEntry;
import com.iadanza.profpublicationsapp.infrastructure.lookup.ProfessorLookupRepository;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Dialog per la Rubrica CF locale.
 *
 * Responsabilità:
 * - mostrare la rubrica locale dei docenti;
 * - filtrare per nome, cognome o codice fiscale;
 * - aggiungere/modificare/eliminare docenti;
 * - inviare alla UI principale il docente selezionato per la ricerca.
 */
public class ProfessorLookupDialog {

    private final ProfessorLookupRepository professorLookupRepository;
    private final Consumer<String> statusConsumer;
    private final Consumer<ProfessorLookupEntry> useForSearchConsumer;

    public ProfessorLookupDialog(
            ProfessorLookupRepository professorLookupRepository,
            Consumer<String> statusConsumer,
            Consumer<ProfessorLookupEntry> useForSearchConsumer
    ) {
        this.professorLookupRepository = professorLookupRepository;
        this.statusConsumer = statusConsumer != null ? statusConsumer : ignored -> { };
        this.useForSearchConsumer = useForSearchConsumer != null ? useForSearchConsumer : ignored -> { };
    }

    public void showAndWait() {
        AtomicReference<List<ProfessorLookupEntry>> allEntries =
                new AtomicReference<>(professorLookupRepository.findAll());

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Rubrica CF");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        DialogSupport.applyDialogIcon(dialog);
        DialogSupport.applyDialogStylesheet(dialog);

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

                    statusConsumer.accept("Docente aggiornato nella rubrica CF: "
                            + editedEntry.get().nome()
                            + " "
                            + editedEntry.get().cognome()
                            + ".");
                } catch (IOException | IllegalArgumentException e) {
                    DialogSupport.showErrorAlert("Errore modifica docente", e.getMessage());
                }
            });

            deleteItem.setOnAction(event -> {
                ProfessorLookupEntry selectedEntry = row.getItem();

                if (selectedEntry == null) {
                    return;
                }

                boolean confirmed = confirmDeleteProfessorLookupEntry(selectedEntry);

                if (!confirmed) {
                    statusConsumer.accept("Eliminazione docente annullata.");
                    return;
                }

                try {
                    professorLookupRepository.delete(selectedEntry);
                    allEntries.set(professorLookupRepository.findAll());
                    refreshLookupTable(filteredEntries, allEntries.get(), filterField.getText());

                    statusConsumer.accept("Docente eliminato dalla rubrica CF: "
                            + selectedEntry.nome()
                            + " "
                            + selectedEntry.cognome()
                            + ".");
                } catch (IOException | IllegalArgumentException e) {
                    DialogSupport.showErrorAlert("Errore eliminazione docente", e.getMessage());
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
                statusConsumer.accept("Seleziona una riga dalla rubrica CF.");
                return;
            }

            dialog.close();

            Platform.runLater(() -> useForSearchConsumer.accept(selectedEntry));
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

                statusConsumer.accept("Docente aggiunto alla rubrica CF: "
                        + newEntry.get().nome()
                        + " "
                        + newEntry.get().cognome()
                        + ".");
            } catch (IOException | IllegalArgumentException e) {
                DialogSupport.showErrorAlert("Errore aggiunta docente", e.getMessage());
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

        DialogSupport.applyDialogIcon(dialog);
        DialogSupport.applyDialogStylesheet(dialog);

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

        DialogSupport.applyDialogIcon(alert);
        DialogSupport.applyDialogStylesheet(alert);

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

        DialogSupport.applyDialogIcon(dialog);
        DialogSupport.applyDialogStylesheet(dialog);

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
                DialogSupport.showErrorAlert("Dati non validi", validationError);
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
}