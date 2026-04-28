package com.iadanza.profpublicationsapp.application.service;

import com.iadanza.profpublicationsapp.infrastructure.config.StartupPreferences;

/**
 * Gestisce le preferenze locali legate al comportamento iniziale
 * dell'applicazione, ad esempio la visualizzazione del popup
 * per il refresh degli indici citazionali.
 */
public interface StartupPreferenceService {

    StartupPreferences loadPreferences();

    void savePreferences(StartupPreferences preferences);
}