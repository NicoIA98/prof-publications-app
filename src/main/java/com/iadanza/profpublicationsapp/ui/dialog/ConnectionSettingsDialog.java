package com.iadanza.profpublicationsapp.ui.dialog;

import com.iadanza.profpublicationsapp.infrastructure.config.ConnectionSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.LocalSettingsRepository;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Dialog per la configurazione locale delle credenziali/API key.
 *
 * Responsabilità:
 * - mostrare i campi per IRIS, Scopus e SerpApi;
 * - mostrare/nascondere temporaneamente password e API key;
 * - mostrare istruzioni operative tramite pulsante Aiuto;
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
        DialogSupport.applyDialogIcon(dialog);
        DialogSupport.applyDialogStylesheet(dialog);

        /*
         * ButtonType tecnico nascosto.
         * Serve a JavaFX per rendere la dialog chiudibile correttamente con X,
         * Esc e chiusura finestra principale, anche se usiamo pulsanti custom.
         */
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);

        Button hiddenCancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        hiddenCancelButton.setVisible(false);
        hiddenCancelButton.setManaged(false);

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

        TextField irisPasswordVisibleField = createVisibleSecretTextField(irisPasswordField);
        StackPane irisPasswordContainer = createSecretFieldContainer(
                irisPasswordField,
                irisPasswordVisibleField
        );

        Label scopusSectionLabel = new Label("Scopus / Elsevier");
        scopusSectionLabel.getStyleClass().add("section-title");

        PasswordField scopusApiKeyField = new PasswordField();
        scopusApiKeyField.setPromptText("SCOPUS_API_KEY");
        scopusApiKeyField.setText(currentSettings.scopusApiKey());

        TextField scopusApiKeyVisibleField = createVisibleSecretTextField(scopusApiKeyField);
        StackPane scopusApiKeyContainer = createSecretFieldContainer(
                scopusApiKeyField,
                scopusApiKeyVisibleField
        );

        PasswordField scopusInstTokenField = new PasswordField();
        scopusInstTokenField.setPromptText("SCOPUS_INST_TOKEN opzionale");
        scopusInstTokenField.setText(currentSettings.scopusInstToken());

        TextField scopusInstTokenVisibleField = createVisibleSecretTextField(scopusInstTokenField);
        StackPane scopusInstTokenContainer = createSecretFieldContainer(
                scopusInstTokenField,
                scopusInstTokenVisibleField
        );

        Label scholarSectionLabel = new Label("Google Scholar tramite SerpApi");
        scholarSectionLabel.getStyleClass().add("section-title");

        PasswordField serpApiKeyField = new PasswordField();
        serpApiKeyField.setPromptText("SERPAPI_API_KEY");
        serpApiKeyField.setText(currentSettings.serpApiApiKey());

        TextField serpApiKeyVisibleField = createVisibleSecretTextField(serpApiKeyField);
        StackPane serpApiKeyContainer = createSecretFieldContainer(
                serpApiKeyField,
                serpApiKeyVisibleField
        );

        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(8);
        formGrid.setPadding(new Insets(10, 0, 0, 0));

        int row = 0;

        formGrid.add(irisSectionLabel, 0, row++, 2, 1);
        formGrid.add(new Label("Username"), 0, row);
        formGrid.add(irisUsernameField, 1, row++);
        formGrid.add(new Label("Password"), 0, row);
        formGrid.add(irisPasswordContainer, 1, row++);

        formGrid.add(scopusSectionLabel, 0, row++, 2, 1);
        formGrid.add(new Label("API key"), 0, row);
        formGrid.add(scopusApiKeyContainer, 1, row++);
        formGrid.add(new Label("Institutional token"), 0, row);
        formGrid.add(scopusInstTokenContainer, 1, row++);

        formGrid.add(scholarSectionLabel, 0, row++, 2, 1);
        formGrid.add(new Label("SerpApi API key"), 0, row);
        formGrid.add(serpApiKeyContainer, 1, row++);

        GridPane.setHgrow(irisUsernameField, Priority.ALWAYS);
        GridPane.setHgrow(irisPasswordContainer, Priority.ALWAYS);
        GridPane.setHgrow(scopusApiKeyContainer, Priority.ALWAYS);
        GridPane.setHgrow(scopusInstTokenContainer, Priority.ALWAYS);
        GridPane.setHgrow(serpApiKeyContainer, Priority.ALWAYS);

        Button toggleSecretsButton = new Button("Mostra API key");
        toggleSecretsButton.getStyleClass().add("secondary-button");
        toggleSecretsButton.setMinWidth(160);
        toggleSecretsButton.setPrefWidth(160);

        Label visibilityHintLabel = new Label(
                "Le API key e la password IRIS sono mascherate di default. "
                        + "Usa questo pulsante solo se devi controllare o correggere i valori."
        );
        visibilityHintLabel.setWrapText(true);

        HBox visibilityBar = new HBox(
                10,
                toggleSecretsButton,
                visibilityHintLabel
        );
        visibilityBar.setAlignment(Pos.CENTER_LEFT);

        toggleSecretsButton.setOnAction(event -> {
            boolean showSecrets = !irisPasswordVisibleField.isVisible();

            setSecretFieldVisibility(irisPasswordField, irisPasswordVisibleField, showSecrets);
            setSecretFieldVisibility(scopusApiKeyField, scopusApiKeyVisibleField, showSecrets);
            setSecretFieldVisibility(scopusInstTokenField, scopusInstTokenVisibleField, showSecrets);
            setSecretFieldVisibility(serpApiKeyField, serpApiKeyVisibleField, showSecrets);

            toggleSecretsButton.setText(showSecrets ? "Nascondi API key" : "Mostra API key");

            statusConsumer.accept(showSecrets
                    ? "API key visibili temporaneamente nella dialog impostazioni."
                    : "API key nuovamente mascherate nella dialog impostazioni.");
        });

        Label helpHintLabel = new Label(
                "Premi \"? Aiuto\" per leggere le istruzioni su come ottenere le API key Scopus e SerpApi."
        );
        helpHintLabel.setWrapText(true);

        Label restartLabel = new Label(
                "Nota: dopo il salvataggio riavvia l'applicazione per applicare le nuove API key ai connector."
        );
        restartLabel.setWrapText(true);

        Button helpButton = new Button("? Aiuto");
        helpButton.getStyleClass().add("cf-help-bib-style-button");
        helpButton.setOnAction(event -> showApiKeyInstructionsHelpDialog());

        Button saveButton = new Button("Salva");
        saveButton.getStyleClass().add("primary-button");

        Button cancelButton = new Button("Annulla");
        cancelButton.setOnAction(event -> dialog.close());

        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);

        HBox actionBar = new HBox(
                12,
                helpButton,
                actionSpacer,
                saveButton,
                cancelButton
        );
        actionBar.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(
                10,
                introLabel,
                pathLabel,
                formGrid,
                visibilityBar,
                helpHintLabel,
                restartLabel,
                actionBar
        );
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(10));
        content.setPrefWidth(820);

        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                dialog.close();
                event.consume();
            }
        });

        saveButton.setOnAction(event -> {
            String validationError = validateConnectionSettingsInput(
                    irisUsernameField.getText(),
                    irisPasswordField.getText(),
                    scopusApiKeyField.getText(),
                    scopusInstTokenField.getText(),
                    serpApiKeyField.getText()
            );

            if (validationError != null) {
                DialogSupport.showErrorAlert("Impostazioni non valide", validationError);
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

                DialogSupport.showInfoAlert(
                        "Impostazioni salvate",
                        "Le impostazioni sono state salvate correttamente.\n\n"
                                + "File locale:\n"
                                + localSettingsRepository.getSettingsPath()
                                + "\n\nRiavvia l'applicazione per applicare le nuove API key."
                );

                dialog.close();
            } catch (IOException e) {
                DialogSupport.showErrorAlert(
                        "Errore salvataggio impostazioni",
                        "Non è stato possibile salvare il file settings.properties."
                );
            }
        });

        dialog.showAndWait();
    }

    private TextField createVisibleSecretTextField(PasswordField passwordField) {
        TextField visibleField = new TextField();
        visibleField.setPromptText(passwordField.getPromptText());
        visibleField.textProperty().bindBidirectional(passwordField.textProperty());
        visibleField.setVisible(false);
        visibleField.setManaged(false);
        return visibleField;
    }

    private StackPane createSecretFieldContainer(
            PasswordField passwordField,
            TextField visibleField
    ) {
        passwordField.setMaxWidth(Double.MAX_VALUE);
        visibleField.setMaxWidth(Double.MAX_VALUE);

        StackPane container = new StackPane(passwordField, visibleField);
        container.setMaxWidth(Double.MAX_VALUE);

        return container;
    }

    private void setSecretFieldVisibility(
            PasswordField passwordField,
            TextField visibleField,
            boolean showSecret
    ) {
        passwordField.setVisible(!showSecret);
        passwordField.setManaged(!showSecret);

        visibleField.setVisible(showSecret);
        visibleField.setManaged(showSecret);
    }

    private void showApiKeyInstructionsHelpDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Aiuto API key");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);
        DialogSupport.applyDialogIcon(dialog);
        DialogSupport.applyDialogStylesheet(dialog);

        TextArea helpArea = new TextArea(buildApiKeyInstructionsText());
        helpArea.setEditable(false);
        helpArea.setWrapText(true);
        helpArea.setPrefSize(720, 520);

        VBox content = new VBox(10, helpArea);
        content.getStyleClass().add("dialog-content");
        content.setPadding(new Insets(10));
        VBox.setVgrow(helpArea, Priority.ALWAYS);

        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private String buildApiKeyInstructionsText() {
        return """
                SCOPUS / ELSEVIER
                1. Vai sul portale Elsevier Developer Portal:
                   https://dev.elsevier.com
                2. Accedi con il tuo account personale o istituzionale.
                3. Crea una nuova API key dal portale sviluppatori.
                4. Inserisci la chiave nel campo:
                   SCOPUS_API_KEY
                5. Il campo SCOPUS_INST_TOKEN è opzionale.
                   Può essere richiesto in alcuni scenari istituzionali.
                   In altri casi l'accesso può dipendere dalla rete di Ateneo, dalla VPN o dall'IP da cui partono le richieste.
                6. Nota per la demo:
                   se Scopus restituisce solo il numero di citazioni ma non i documenti citanti,
                   l'app continuerà a funzionare in modalità PARTIAL_DATA.

                GOOGLE SCHOLAR TRAMITE SERPAPI
                1. Vai sulla dashboard SerpApi:
                   https://serpapi.com/dashboard
                2. Crea o recupera la tua API key personale.
                3. Inserisci la chiave nel campo:
                   SERPAPI_API_KEY
                4. L'app usa SerpApi per interrogare Google Scholar, senza scraping diretto custom.
                5. Per la ricerca Scholar viene usato l'engine:
                   google_scholar
                6. In una versione futura, per le citazioni esportabili potrà essere usato anche:
                   google_scholar_cite
                   con result_id quando disponibile.
                7. Nota:
                   la disponibilità di risultati, citazioni e documenti citanti dipende dai dati esposti da SerpApi,
                   dai limiti del piano attivo e dalla risposta di Google Scholar.

                SICUREZZA LOCALE
                - Le API key vengono salvate solo nel file locale settings.properties.
                - Non condividere screenshot o log contenenti API key.
                - L'app non deve stampare password o API key in console.
                """;
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
}