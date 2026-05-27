package com.iadanza.profpublicationsapp.ui.dialog;

import com.iadanza.profpublicationsapp.infrastructure.config.ConnectionSettings;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Dialog mostrata all'avvio quando la configurazione locale non è completa.
 *
 * Responsabilità:
 * - informare l'utente che l'app richiede credenziali/API key personali;
 * - distinguere tra dati istituzionali IRIS, Scopus e Scholar/SerpApi;
 * - permettere di aprire subito la dialog Impostazioni;
 * - permettere di continuare in modalità limitata.
 */
public class StartupSettingsWarningDialog {

    private final ConnectionSettings settings;
    private final Path settingsPath;

    public StartupSettingsWarningDialog(ConnectionSettings settings, Path settingsPath) {
        this.settings = settings != null ? settings.normalized() : ConnectionSettings.empty();
        this.settingsPath = settingsPath;
    }

    public boolean shouldShow() {
        return !missingItems().isEmpty();
    }

    public boolean showAndWaitForOpenSettings() {
        List<String> missingItems = missingItems();

        if (missingItems.isEmpty()) {
            return false;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Configurazione iniziale richiesta");
        dialog.setResizable(true);
        applyDialogIcon(dialog);
        applyDialogStylesheet(dialog);

        ButtonType openSettingsButtonType = new ButtonType(
                "Apri impostazioni",
                ButtonBar.ButtonData.OK_DONE
        );

        ButtonType continueLimitedButtonType = new ButtonType(
                "Continua in modalità limitata",
                ButtonBar.ButtonData.CANCEL_CLOSE
        );

        dialog.getDialogPane().getButtonTypes().addAll(
                openSettingsButtonType,
                continueLimitedButtonType
        );

        Label titleLabel = new Label("Configurazione iniziale richiesta");
        titleLabel.getStyleClass().add("section-title");
        titleLabel.setWrapText(true);

        Label introLabel = new Label(
                "Per usare correttamente l'applicazione, ogni utente deve configurare "
                        + "le proprie credenziali/API key personali. "
                        + "Senza queste informazioni alcune funzioni resteranno non disponibili."
        );
        introLabel.setWrapText(true);

        TextArea missingArea = new TextArea(buildMissingItemsText(missingItems));
        missingArea.setEditable(false);
        missingArea.setWrapText(true);
        missingArea.setPrefRowCount(8);

        Label pathLabel = new Label(
                "File locale impostazioni:\n"
                        + (settingsPath != null ? settingsPath.toString() : "Percorso non disponibile")
        );
        pathLabel.setWrapText(true);

        Label noteLabel = new Label(
                "Le credenziali vengono salvate solo localmente sul PC e non devono essere caricate su Git. "
                        + "Dopo il salvataggio delle impostazioni è consigliato riavviare l'applicazione."
        );
        noteLabel.setWrapText(true);

        VBox content = new VBox(
                10,
                titleLabel,
                introLabel,
                missingArea,
                pathLabel,
                noteLabel
        );
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(10));
        content.setPrefWidth(680);

        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> result = dialog.showAndWait();

        return result.isPresent() && result.get() == openSettingsButtonType;
    }

    private String buildMissingItemsText(List<String> missingItems) {
        StringBuilder builder = new StringBuilder();

        builder.append("Configurazioni mancanti o incomplete:\n\n");

        for (String item : missingItems) {
            builder.append("• ").append(item).append("\n");
        }

        builder.append("\nEffetto sulla demo:\n");
        builder.append("• senza IRIS: ricerca/refresh istituzionale non disponibile;\n");
        builder.append("• senza Scopus: citation count Scopus non disponibile;\n");
        builder.append("• senza SerpApi: Scholar e documenti citanti Scholar non disponibili.");

        return builder.toString();
    }

    private List<String> missingItems() {
        List<String> missingItems = new ArrayList<>();

        boolean irisUsernameConfigured = hasText(settings.irisRestUsername());
        boolean irisPasswordConfigured = hasText(settings.irisRestPassword());

        if (!irisUsernameConfigured || !irisPasswordConfigured) {
            missingItems.add("IRIS / CINECA: username e password REST");
        }

        if (!hasText(settings.scopusApiKey())) {
            missingItems.add("Scopus / Elsevier: SCOPUS_API_KEY");
        }

        if (!hasText(settings.serpApiApiKey())) {
            missingItems.add("Google Scholar tramite SerpApi: SERPAPI_API_KEY");
        }

        return missingItems;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
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