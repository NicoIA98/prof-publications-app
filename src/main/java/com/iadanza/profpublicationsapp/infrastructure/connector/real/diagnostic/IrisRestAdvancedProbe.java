package com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Probe REST avanzato per endpoint Cineca/IRIS.
 *
 * Non usa ancora autenticazione.
 * Serve solo per capire:
 * - se il path esiste
 * - se richiede auth
 * - se fa redirect
 * - se risponde con JSON/HTML/altro
 */
public class IrisRestAdvancedProbe {

    private final HttpClient httpClient;
    private final String baseUrl;

    public IrisRestAdvancedProbe(HttpClient httpClient, String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = normalizeBaseUrl(baseUrl);
    }

    public List<RestEndpointProbeResult> probeAll() {
        return List.of(
                probeGet("/rest/api/v1/echo"),
                probeGet("/rest/api/v1/items/search"),
                probePostJson("/rest/api/v1/items/search", "{}"),
                probeGet("/rm/restservices/api/v1/echo"),
                probeGet("/rm/restservices/api/v1/personsbyrpid/test")
        );
    }

    private RestEndpointProbeResult probeGet(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Accept", "application/json, application/hal+json, */*")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return toResult("GET", path, response);

        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            return new RestEndpointProbeResult(
                    "GET",
                    path,
                    -1,
                    null,
                    null,
                    "",
                    false,
                    false,
                    false,
                    "Errore probe: " + e.getClass().getSimpleName() + " - " + e.getMessage()
            );
        }
    }

    private RestEndpointProbeResult probePostJson(String path, String jsonBody) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Accept", "application/json, application/hal+json, */*")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return toResult("POST", path, response);

        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            return new RestEndpointProbeResult(
                    "POST",
                    path,
                    -1,
                    null,
                    null,
                    "",
                    false,
                    false,
                    false,
                    "Errore probe: " + e.getClass().getSimpleName() + " - " + e.getMessage()
            );
        }
    }

    private RestEndpointProbeResult toResult(String method, String path, HttpResponse<String> response) {
        int status = response.statusCode();
        String location = response.headers().firstValue("Location").orElse(null);
        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        String body = response.body() != null ? response.body() : "";
        String bodyPreview = body.length() > 240 ? body.substring(0, 240) + "..." : body;

        boolean redirected = status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
        boolean authLikelyRequired = status == 401 || status == 403;

        boolean endpointExistsLikely =
                status == 200 ||
                        status == 201 ||
                        status == 202 ||
                        status == 204 ||
                        status == 400 ||
                        status == 401 ||
                        status == 403 ||
                        status == 404 ||
                        status == 405 ||
                        status == 415;

        String notes = buildNotes(status, redirected, authLikelyRequired, location, contentType);

        return new RestEndpointProbeResult(
                method,
                path,
                status,
                location,
                contentType,
                bodyPreview,
                endpointExistsLikely,
                authLikelyRequired,
                redirected,
                notes
        );
    }

    private String buildNotes(int status, boolean redirected, boolean authLikelyRequired, String location, String contentType) {
        if (redirected) {
            return "Redirect rilevato" + (location != null ? " verso: " + location : "");
        }

        if (authLikelyRequired) {
            return "Endpoint probabilmente esistente ma protetto/autenticato";
        }

        if (status == 405) {
            return "Endpoint probabilmente esistente, ma metodo HTTP non corretto";
        }

        if (status == 404) {
            return "Path non trovato oppure non esposto pubblicamente";
        }

        if (status == 400 || status == 415) {
            return "Endpoint probabilmente esistente, ma payload/header non attesi";
        }

        if (status == 200) {
            return "Risposta positiva" + (contentType != null ? " (" + contentType + ")" : "");
        }

        return "Risposta ricevuta";
    }

    private String normalizeBaseUrl(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}