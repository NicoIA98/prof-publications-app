package com.iadanza.profpublicationsapp.infrastructure.connector.real;

import com.iadanza.profpublicationsapp.infrastructure.config.IrisRuntimeSettings;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.EnumSet;
import java.util.Set;

/**
 * Probe tecnico dell'istanza IRIS.
 *
 * In A1 non recupera ancora professori o pubblicazioni:
 * verifica solo se esistono capability tecniche utili.
 */
public class IrisCapabilityProbe {

    private final HttpClient httpClient;
    private final IrisRuntimeSettings settings;

    public IrisCapabilityProbe(HttpClient httpClient, IrisRuntimeSettings settings) {
        this.httpClient = httpClient;
        this.settings = settings;
    }

    public IrisProbeResult detectCapabilities() {
        ProbeCheck restCheck = probeRestDiscoverySearch();
        ProbeCheck oaiCheck = probeOaiPmhIdentify();

        EnumSet<IrisCapability> capabilities = EnumSet.noneOf(IrisCapability.class);

        if (restCheck.supported()) {
            capabilities.add(IrisCapability.REST_DISCOVERY_SEARCH);
        }

        if (oaiCheck.supported()) {
            capabilities.add(IrisCapability.OAI_PMH);
        }

        String notes = buildNotes(restCheck, oaiCheck, capabilities);

        return new IrisProbeResult(
                settings.baseUrl(),
                restCheck.statusCode(),
                oaiCheck.statusCode(),
                restCheck.supported(),
                oaiCheck.supported(),
                Set.copyOf(capabilities),
                notes
        );
    }

    private ProbeCheck probeRestDiscoverySearch() {
        String url = normalizeBaseUrl(settings.baseUrl())
                + settings.restSearchPath()
                + "?query=test&page=0&size=1";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(settings.timeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body() != null ? response.body() : "";

            boolean supported = statusCode == 200
                    && (body.contains("_embedded")
                    || body.contains("\"page\"")
                    || body.contains("searchResult")
                    || body.contains("_links"));

            return new ProbeCheck(statusCode, supported);

        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            return new ProbeCheck(-1, false);
        }
    }

    private ProbeCheck probeOaiPmhIdentify() {
        String url = normalizeBaseUrl(settings.baseUrl()) + settings.oaiIdentifyPath();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(settings.timeoutSeconds()))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String body = response.body() != null ? response.body() : "";

            boolean supported = statusCode == 200
                    && (body.contains("<OAI-PMH")
                    || body.contains("<Identify>")
                    || body.contains("ListMetadataFormats"));

            return new ProbeCheck(statusCode, supported);

        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            return new ProbeCheck(-1, false);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String buildNotes(ProbeCheck restCheck, ProbeCheck oaiCheck, Set<IrisCapability> capabilities) {
        if (!capabilities.isEmpty()) {
            return "Capability rilevate: " + capabilities;
        }

        return "Nessuna capability rilevata. REST status="
                + restCheck.statusCode()
                + ", OAI status="
                + oaiCheck.statusCode();
    }

    private record ProbeCheck(int statusCode, boolean supported) {
    }
}