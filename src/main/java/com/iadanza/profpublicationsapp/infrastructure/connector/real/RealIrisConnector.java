package com.iadanza.profpublicationsapp.infrastructure.connector.real;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisRuntimeSettings;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;

/**
 * Connettore IRIS reale - fase A1.
 *
 * In questa fase:
 * - esegue il probe tecnico dell'istanza
 * - non implementa ancora la ricerca reale dei professori
 * - non implementa ancora il fetch reale delle pubblicazioni
 */
public class RealIrisConnector implements IrisConnector {

    private final HttpClient httpClient;
    private final IrisRuntimeSettings settings;
    private final IrisProbeResult probeResult;

    public RealIrisConnector(HttpClient httpClient, IrisRuntimeSettings settings) {
        this.httpClient = httpClient;
        this.settings = settings;
        this.probeResult = new IrisCapabilityProbe(httpClient, settings).detectCapabilities();
    }

    public IrisProbeResult getProbeResult() {
        return probeResult;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public IrisRuntimeSettings getSettings() {
        return settings;
    }

    @Override
    public List<Professor> searchProfessors(String query) {
        return List.of();
    }

    @Override
    public Optional<Professor> findProfessorByIdentifier(IdentifierType identifierType, String value) {
        return Optional.empty();
    }

    @Override
    public List<Publication> fetchProfessorPublications(Professor professor) {
        return List.of();
    }

    @Override
    public Optional<BibtexEntry> fetchBibtexEntry(Publication publication) {
        return Optional.empty();
    }
}