package com.iadanza.profpublicationsapp.infrastructure.connector.real;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iadanza.profpublicationsapp.domain.enums.IdentifierType;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.ExternalIdentifier;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisRestAuthSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisRuntimeSettings;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic.AuthenticatedRestCallResult;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic.IrisCapabilityProbe;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic.IrisProbeResult;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.dto.IrisItemSearchResponseDto;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.dto.IrisPersonRestDto;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Connettore IRIS reale.
 *
 * In A4:
 * - cerca professore reale per crisId (IRIS ID)
 * - recupera pubblicazioni reali via items/search
 * - mappa gli item REST in Publication
 */
public class RealIrisConnector implements IrisConnector {

    private final HttpClient httpClient;
    private final IrisRuntimeSettings settings;
    private final IrisProbeResult probeResult;
    private final IrisRestAuthSettings authSettings;
    private final ObjectMapper objectMapper;
    private final IrisRestPublicationMapper publicationMapper;

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
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.publicationMapper = new IrisRestPublicationMapper(authSettings != null ? authSettings.baseUrl() : settings.baseUrl());
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

    @Override
    public List<Professor> searchProfessors(String query) {
        return List.of();
    }

    @Override
    public Optional<Professor> findProfessorByIdentifier(IdentifierType identifierType, String value) {
        if (identifierType != IdentifierType.IRIS_ID || value == null || value.isBlank()) {
            return Optional.empty();
        }

        if (!hasAuthenticatedRestConfiguration()) {
            return Optional.empty();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildRmUrl("personsbyrpid/" + value)))
                    .timeout(java.time.Duration.ofSeconds(authSettings.timeoutSeconds()))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Authorization", buildBasicAuthHeader())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            IrisPersonRestDto personDto = objectMapper.readValue(response.body(), IrisPersonRestDto.class);

            Professor professor = new Professor(
                    personDto.firstName(),
                    personDto.lastName(),
                    buildFullName(personDto.firstName(), personDto.lastName()),
                    "Università degli Studi di Cassino e del Lazio Meridionale",
                    List.of(new ExternalIdentifier(
                            IdentifierType.IRIS_ID,
                            personDto.crisId(),
                            SourceType.IRIS
                    ))
            );

            return Optional.of(professor);

        } catch (IOException | InterruptedException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Publication> fetchProfessorPublications(Professor professor) {
        Optional<String> crisId = professor.externalIdentifiers().stream()
                .filter(identifier -> identifier.type() == IdentifierType.IRIS_ID)
                .map(ExternalIdentifier::value)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();

        if (crisId.isEmpty() || !hasAuthenticatedRestConfiguration()) {
            return List.of();
        }

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
                  "limit": 100,
                  "expand": "all",
                  "operator": "AND"
                }
                """.formatted(crisId.get());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildIrUrl("items/search")))
                    .timeout(java.time.Duration.ofSeconds(authSettings.timeoutSeconds()))
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Content-Type", "application/json")
                    .header("Authorization", buildBasicAuthHeader())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return List.of();
            }

            IrisItemSearchResponseDto dto = objectMapper.readValue(response.body(), IrisItemSearchResponseDto.class);

            if (dto.restResourceDtoList() == null) {
                return List.of();
            }

            return dto.restResourceDtoList().stream()
                    .map(publicationMapper::toDomainPublication)
                    .toList();

        } catch (IOException | InterruptedException e) {
            return List.of();
        }
    }

    @Override
    public Optional<BibtexEntry> fetchBibtexEntry(Publication publication) {
        return Optional.empty();
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

    private String buildFullName(String firstName, String lastName) {
        String first = firstName != null ? firstName.trim() : "";
        String last = lastName != null ? lastName.trim() : "";
        return (first + " " + last).trim();
    }
}