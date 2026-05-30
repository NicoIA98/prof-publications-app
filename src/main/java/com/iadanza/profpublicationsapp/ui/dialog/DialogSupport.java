package com.iadanza.profpublicationsapp.ui.dialog;

import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Utility condivisa per le dialog JavaFX dell'applicazione.
 *
 * Responsabilità:
 * - applicare stylesheet comune;
 * - applicare icona comune;
 * - mostrare alert informativi o di errore con stile coerente.
 */
public final class DialogSupport {

    private static final String APP_STYLESHEET_PATH = "/styles/app.css";
    private static final String APP_ICON_PATH = "/icons/app-icon.png";

    private DialogSupport() {
    }

    public static void applyDialogStylesheet(Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }

        URL stylesheet = DialogSupport.class.getResource(APP_STYLESHEET_PATH);

        if (stylesheet == null) {
            System.out.println("Stylesheet dialog non trovato: " + APP_STYLESHEET_PATH);
            return;
        }

        dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
    }

    public static void applyDialogIcon(Dialog<?> dialog) {
        if (dialog == null) {
            return;
        }

        URL iconUrl = DialogSupport.class.getResource(APP_ICON_PATH);

        if (iconUrl == null) {
            System.out.println("Icona dialog non trovata: " + APP_ICON_PATH);
            return;
        }

        dialog.setOnShown(event -> {
            Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
            dialogStage.getIcons().add(new Image(iconUrl.toExternalForm()));
        });
    }

    public static void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "");

        applyDialogIcon(alert);
        applyDialogStylesheet(alert);

        alert.showAndWait();
    }

    public static void showErrorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message != null ? message : "Errore non specificato.");

        applyDialogIcon(alert);
        applyDialogStylesheet(alert);

        alert.showAndWait();
    }
}