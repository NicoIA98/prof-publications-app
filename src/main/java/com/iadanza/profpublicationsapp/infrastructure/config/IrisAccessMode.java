package com.iadanza.profpublicationsapp.infrastructure.config;

/**
 * Modalità di accesso al backend IRIS.
 *
 * AUTO:
 *  prova a rilevare automaticamente le capability disponibili.
 *
 * DSPACE_REST:
 *  forza l'uso di endpoint REST in stile DSpace 7+.
 *
 * OAI_PMH:
 *  forza l'uso di endpoint OAI-PMH.
 */
public enum IrisAccessMode {
    AUTO,
    DSPACE_REST,
    OAI_PMH
}