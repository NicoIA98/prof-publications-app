package com.iadanza.profpublicationsapp.ui.dialog;

import com.iadanza.profpublicationsapp.application.service.BibtexService;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Dialog per visualizzare, copiare e salvare BibTeX.
 *
 * Responsabilità:
 * - mostrare BibTeX di una pubblicazione;
 * - mostrare BibTeX fallback di un documento citante;
 * - copiare BibTeX negli appunti;
 * - salvare BibTeX su file .bib;
 * - mantenere fuori da ProfessorPublicationsApp la logica UI BibTeX.
 */
public class BibtexDialog {

    private final BibtexService bibtexService;
    private final Window ownerWindow;
    private final Consumer<String> statusConsumer;

    public BibtexDialog(
            BibtexService bibtexService,
            Window ownerWindow,
            Consumer<String> statusConsumer
    ) {
        this.bibtexService = bibtexService;
        this.ownerWindow = ownerWindow;
        this.statusConsumer = statusConsumer != null ? statusConsumer : ignored -> { };
    }

    public void showForPublication(Publication publication) {
        if (publication == null) {
            statusConsumer.accept("Seleziona prima una pubblicazione.");
            return;
        }

        Optional<BibtexEntry> result = bibtexService.resolveBibtex(publication);

        if (result.isEmpty()) {
            statusConsumer.accept("Impossibile generare o recuperare il BibTeX.");
            return;
        }

        BibtexEntry bibtexEntry = result.get();
        statusConsumer.accept("BibTeX ottenuto da sorgente: " + bibtexEntry.sourceType() + ".");

        showBibtexDialog(
                "BibTeX - " + safeDisplayText(publication.title()),
                bibtexEntry
        );
    }

    public void showForCitingDocument(CitingDocument document) {
        if (document == null) {
            statusConsumer.accept("Seleziona prima un documento citante.");
            return;
        }

        Optional<BibtexEntry> result = generateBibtexForCitingDocument(document);

        if (result.isEmpty()) {
            statusConsumer.accept("Impossibile generare il BibTeX per il documento citante.");
            return;
        }

        BibtexEntry bibtexEntry = result.get();
        statusConsumer.accept("BibTeX generato per documento citante da sorgente: "
                + bibtexEntry.sourceType()
                + ".");

        showBibtexDialog(
                "BibTeX documento citante - " + safeDisplayText(document.title()),
                bibtexEntry
        );
    }

    private void showBibtexDialog(String title, BibtexEntry bibtexEntry) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        if (ownerWindow != null) {
            dialog.initOwner(ownerWindow);
        }

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

    private void copyBibtexToClipboard(String bibtexText) {
        ClipboardContent content = new ClipboardContent();
        content.putString(bibtexText);
        Clipboard.getSystemClipboard().setContent(content);
        statusConsumer.accept("BibTeX copiato negli appunti.");
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

        File selectedFile = fileChooser.showSaveDialog(ownerWindow);

        if (selectedFile == null) {
            statusConsumer.accept("Salvataggio BibTeX annullato.");
            return;
        }

        try {
            Files.writeString(selectedFile.toPath(), bibtexEntry.rawBibtex());
            statusConsumer.accept("BibTeX salvato in: " + selectedFile.getAbsolutePath());
        } catch (IOException e) {
            statusConsumer.accept("Errore durante il salvataggio del file BibTeX.");
            showErrorAlert("Errore salvataggio file", "Impossibile salvare il file .bib selezionato.");
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
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

    private void applyDialogStylesheet(Dialog<?> dialog) {
        URL stylesheet = getClass().getResource("/styles/app.css");

        if (stylesheet == null) {
            System.out.println("Stylesheet dialog non trovato: /styles/app.css");
            return;
        }

        dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
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
}