package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Contiene le impostazioni minime di connessione e integrazione
 * per le sorgenti esterne e per la persistenza locale.
 *
 * In v1 viene usato come contenitore semplice dei parametri letti
 * da configurazione esterna o inseriti nella schermata di setup.
 */
public record ConnectionSettings(
        String irisBaseUrl,
        String scopusApiKey,
        String scopusBaseUrl,
        String serpApiKey,
        String serpApiBaseUrl,
        String sqliteUrl,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        int maxRetries
) {
}