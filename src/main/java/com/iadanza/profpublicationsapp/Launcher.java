package com.iadanza.profpublicationsapp;

/**
 * Entry point tecnico per l'avvio da JAR o da script.
 *
 * Questa classe non estende javafx.application.Application.
 * Serve come main class stabile per l'esecuzione dell'applicazione
 * al di fuori di IntelliJ IDEA.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        ProfessorPublicationsApp.main(args);
    }
}