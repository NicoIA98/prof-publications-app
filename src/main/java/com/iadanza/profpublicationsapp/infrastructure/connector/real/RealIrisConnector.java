package com.iadanza.profpublicationsapp.infrastructure.connector.real;

import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisRestAuthSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisRuntimeSettings;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Connettore IRIS reale - fase A3.
 *
 * In questa fase:
 * - mantiene il probe capability A1
 * - mantiene i test REST autenticati A2
 * - corregge il payload di items/search con i campi realmente attesi dal server
 */
public class RealIrisConnector implements IrisConnector {

    private final HttpClient httpClient;
    private final IrisRuntimeSettings settings;
    private final IrisProbeResult probeResult;
    private final IrisRestAuthSettings authSettings;

    public RealIrisConnector(HttpClient httpClient, IrisRuntimeSettings settings) {
        this(httpClient, settings, null);
    }

    public RealIrisConnector(
            HttpClient httpClient,
            IrisRuntimeSettings settings,
            IrisRestAuthSettings authSettings
    ) {
        this.httpClient = httpClient;
        this.settings = settings;
        this.authSettings = authSettings;
        this.probeResult = new IrisCapabilityProbe(httpClient, settings).detectCapabilities();
    }

    public IrisProbeResult getProbeResult() {
        return probeResult;
    }

    public boolean hasAuthenticatedRestConfiguration() {
        return authSettings != null && authSettings.isConfigured();
    }

    public AuthenticatedRestCallResult probeAuthenticatedIrEcho() {
        return sendAuthenticatedGet(buildIrUrl("echo"), "GET", "/rest/api/v1/echo");
    }

    public AuthenticatedRestCallResult probeAuthenticatedRmEcho() {
        return sendAuthenticatedGet(buildRmUrl("echo"), "GET", "/rm/restservices/api/v1/echo");
    }

    public AuthenticatedRestCallResult probeAuthenticatedPersonByCrisId(String crisId) {
        return sendAuthenticatedGet(
                buildRmUrl("personsbyrpid/" + crisId),
                "GET",
                "/rm/restservices/api/v1/personsbyrpid/" + crisId
        );
    }

    /**
     * Cerca gli item/pubblicazioni associati a un context user (crisId)
     * usando il payload corretto atteso dalla REST IRIS.
     */
    public AuthenticatedRestCallResult probeAuthenticatedItemsByContextUser(String crisId) {
        String jsonBody = """
                {
                  "searchColsCriteria": [
                    {
                      "column": "lookupValues_contextuser",
                      "operation": "=",
                      "value": "%s"
                    }
                  ],
                  "sortingColsCriteria": [
                    {
                      "column": "lookupValues_contextuser",
                      "asc": true
                    }
                  ],
                  "offset": 0,
                  "limit": 20,
                  "expand": "all",
                  "operator": "AND"
                }
                """.formatted(crisId);

        return sendAuthenticatedPostJson(
                buildIrUrl("items/search"),
                "POST",
                "/rest/api/v1/items/search",
                jsonBody
        );
    }

    /**
     * Variante con filtro per anno, utile per verificare una ricerca più simile
     * a quella usata nel codice dell'ingegnere.
     */
    public AuthenticatedRestCallResult probeAuthenticatedItemsByContextUserAndYear(String crisId, String year) {
        String jsonBody = """
                {
                  "searchColsCriteria": [
                    {
                      "column": "lookupValues_contextuser",
                      "operation": "=",
                      "value": "%s"
                    },
                    {
                      "column": "lookupValues_year",
                      "operation": "=",
                      "value": "%s"
                    }
                  ],
                  "sortingColsCriteria": [
                    {
                      "column": "lookupValues_contextuser",
                      "asc": true
                    }
                  ],
                  "offset": 0,
                  "limit": 20,
                  "expand": "all",
                  "operator": "AND"
                }
                """.formatted(crisId, year);

        return sendAuthenticatedPostJson(
                buildIrUrl("items/search"),
                "POST",
                "/rest/api/v1/items/search",
                jsonBody
        );
    }

    private AuthenticatedRestCallResult sendAuthenticatedGet(String url, String method, String path) {
        if (!hasAuthenticatedRestConfiguration()) {
            return new AuthenticatedRestCallResult(
                    method,
                    path,
                    -1,
                    null,
                    "",
                    "Credenziali REST non configurate"
            );
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(authSettings.timeoutSeconds()))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Authorization", buildBasicAuthHeader())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return toResult(method, path, response);

        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            return new AuthenticatedRestCallResult(
                    method,
                    path,
                    -1,
                    null,
                    "",
                    "Errore chiamata autenticata: " + e.getClass().getSimpleName() + " - " + e.getMessage()
            );
        }
    }

    private AuthenticatedRestCallResult sendAuthenticatedPostJson(
            String url,
            String method,
            String path,
            String jsonBody
    ) {
        if (!hasAuthenticatedRestConfiguration()) {
            return new AuthenticatedRestCallResult(
                    method,
                    path,
                    -1,
                    null,
                    "",
                    "Credenziali REST non configurate"
            );
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(authSettings.timeoutSeconds()))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json")
                    .header("Authorization", buildBasicAuthHeader())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return toResult(method, path, response);

        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            return new AuthenticatedRestCallResult(
                    method,
                    path,
                    -1,
                    null,
                    "",
                    "Errore chiamata autenticata: " + e.getClass().getSimpleName() + " - " + e.getMessage()
            );
        }
    }

    private AuthenticatedRestCallResult toResult(String method, String path, HttpResponse<String> response) {
        String body = response.body() != null ? response.body() : "";
        String preview = body.length() > 300 ? body.substring(0, 300) + "..." : body;
        String contentType = response.headers().firstValue("Content-Type").orElse(null);

        String notes;
        if (response.statusCode() == 200) {
            notes = "Chiamata autenticata riuscita";
        } else if (response.statusCode() == 401) {
            notes = "Credenziali non valide o token Basic non accettato";
        } else if (response.statusCode() == 403) {
            notes = "Credenziali valide ma accesso negato";
        } else if (response.statusCode() == 404) {
            notes = "Endpoint non trovato";
        } else if (response.statusCode() == 400) {
            notes = "Richiesta accettata dal server ma payload/parametri non corretti";
        } else {
            notes = "Risposta ricevuta";
        }

        return new AuthenticatedRestCallResult(
                method,
                path,
                response.statusCode(),
                contentType,
                preview,
                notes
        );
    }

    private String buildBasicAuthHeader() {
        String raw = authSettings.username() + ":" + authSettings.password();
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private String buildIrUrl(String relativePath) {
        String base = normalizeBaseUrl(authSettings.baseUrl());
        String path = normalizePath(authSettings.pathIR());
        return base + "/" + path + "/" + relativePath;
    }

    private String buildRmUrl(String relativePath) {
        String base = normalizeBaseUrl(authSettings.baseUrl());
        String path = normalizePath(authSettings.pathRM());
        return base + "/" + path + "/" + relativePath;
    }

    private String normalizeBaseUrl(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizePath(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
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