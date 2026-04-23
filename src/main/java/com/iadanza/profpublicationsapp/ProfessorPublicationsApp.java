package com.iadanza.profpublicationsapp;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Classe principale dell'applicazione JavaFX.
 * Estende Application ed è il punto di avvio della GUI.
 * Nel metodo start(...) crea una finestra base con un layout semplice
 * e mostra un primo contenuto testuale per verificare che il progetto
 * JavaFX sia configurato correttamente e si avvii senza errori.
 */
public class ProfessorPublicationsApp extends Application {

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.setCenter(new Label("Professor Publications App - avvio riuscito"));

        Scene scene = new Scene(root, 1000, 700);

        stage.setTitle("Professor Publications App");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}