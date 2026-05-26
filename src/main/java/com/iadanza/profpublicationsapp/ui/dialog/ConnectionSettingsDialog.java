package com.iadanza.profpublicationsapp.ui.dialog;

import com.iadanza.profpublicationsapp.infrastructure.config.ConnectionSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.LocalSettingsRepository;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Dialog per la configurazione locale delle credenziali/API key.
 *
 * Responsabilità:
 * - mostrare i campi per IRIS, Scopus e SerpApi;
 * - validare input minimi;
 * - salvare settings.properties tramite LocalSettingsRepository;
 * - non stampare mai API key o password nei log.
 */
public class ConnectionSettingsDialog {

    private final LocalSettingsRepository localSettingsRepository;
    private final Consumer<String> statusConsumer;

    public ConnectionSettingsDialog(
            LocalSettingsRepository localSettingsRepository,
            Consumer<String> statusConsumer
    ) {
        this.localSettingsRepository = localSettingsRepository;
        this.statusConsumer = statusConsumer != null ? statusConsumer : ignored -> { };
    }

    public void showAndWait() {
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

                statusConsumer.accept(
                        "Impostazioni salvate. Riavvia l'applicazione per applicare le nuove API key."
                );

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
            statusConsumer.accept("Impossibile leggere settings.properties. Uso valori vuoti/default.");
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

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
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