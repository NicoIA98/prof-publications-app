package com.iadanza.profpublicationsapp.infrastructure.connector.real;

import java.util.Set;

/**
 * Risultato del probe tecnico dell'istanza IRIS.
 */
public record IrisProbeResult(
        String baseUrl,
        int restStatusCode,
        int oaiStatusCode,
        boolean restSupported,
        boolean oaiSupported,
        Set<IrisCapability> capabilities,
        String notes
) {
}