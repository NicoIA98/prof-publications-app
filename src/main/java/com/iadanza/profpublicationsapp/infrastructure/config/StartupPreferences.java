package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Preferenze locali relative al comportamento dell'applicazione
 * all'avvio o all'apertura della schermata del professore.
 */
public record StartupPreferences(
        boolean showCitationRefreshPopupOnOpen
) {
}