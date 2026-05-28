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
import com.iadanza.profpublicationsapp.infrastructure.connector.real.dto.IrisItemDto;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.dto.IrisItemSearchResponseDto;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.dto.IrisPersonRestDto;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Connettore IRIS reale.
 *
 * B2-bis:
 * - recupera tutte le pagine da items/search;
 * - gestisce il limite reale imposto dal server IRIS/CINECA;
 * - se il server restituisce 51 record anche quando chiediamo 100, continua con offset 51, 102, ecc.;
 * - usa count, offset e numero reale di item restituiti;
 * - mantiene log diagnostici per verificare il numero di record recuperati.
 *
 * Nota sicurezza:
 * - non stampa password IRIS o token Basic;
 * - non stampa body JSON autenticati contenenti dati anagrafici o metadati IRIS;
 * - mantiene visibile solo il body preview degli endpoint /echo, perché non contiene dati sensibili.
 */
public class RealIrisConnector implements IrisConnector {

    private static final int ITEMS_PAGE_SIZE = 100;
    private static final int MAX_ITEMS_PAGES = 50;

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
        this.publicationMapper = new IrisRestPublicationMapper(
                authSettings != null ? authSettings.baseUrl() : settings.baseUrl()
        );
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
        String jsonBody = buildItemsSearchBody(crisId, null, 0, 20);

        return sendAuthenticatedPostJson(
                buildIrUrl("items/search"),
                "POST",
                "/rest/api/v1/items/search",
                jsonBody
        );
    }

    public AuthenticatedRestCallResult probeAuthenticatedItemsByContextUserAndYear(String crisId, String year) {
        String jsonBody = buildItemsSearchBody(crisId, year, 0, 20);

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
        if (value == null || value.isBlank() || !hasAuthenticatedRestConfiguration()) {
            return Optional.empty();
        }

        if (identifierType == IdentifierType.IRIS_ID) {
            return findProfessorByCrisId(value.trim());
        }

        if (identifierType == IdentifierType.CODICE_FISCALE) {
            return findProfessorByCodiceFiscale(value.trim());
        }

        return Optional.empty();
    }

    private Optional<Professor> findProfessorByCrisId(String crisId) {
        try {
            HttpResponse<String> response = sendAuthenticatedGetResponse(
                    buildRmUrl("personsbyrpid/" + encodePathSegment(crisId))
            );

            if (response.statusCode() != 200) {
                return Optional.empty();
            }

            Optional<IrisPersonRestDto> personDto = readPersonDto(response.body());
            return personDto.map(dto -> toProfessor(dto, null));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private Optional<Professor> findProfessorByCodiceFiscale(String codiceFiscale) {
        String encodedCf = encodePathSegment(codiceFiscale);

        List<String> candidatePaths = List.of(
                "personsbycf/" + encodedCf,
                "personbycf/" + encodedCf,
                "personsbyfiscalcode/" + encodedCf,
                "personbyfiscalcode/" + encodedCf,
                "personsbytaxcode/" + encodedCf,
                "personbytaxcode/" + encodedCf
        );

        for (String candidatePath : candidatePaths) {
            try {
                HttpResponse<String> response = sendAuthenticatedGetResponse(buildRmUrl(candidatePath));

                if (response.statusCode() != 200) {
                    continue;
                }

                Optional<IrisPersonRestDto> person = readPersonDto(response.body());
                if (person.isPresent()) {
                    return Optional.of(toProfessor(person.get(), codiceFiscale));
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (IOException | IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private Optional<IrisPersonRestDto> readPersonDto(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }

        String trimmed = body.trim();

        if (trimmed.startsWith("[")) {
            IrisPersonRestDto[] people = objectMapper.readValue(trimmed, IrisPersonRestDto[].class);
            if (people.length == 0) {
                return Optional.empty();
            }
            return Optional.ofNullable(people[0]);
        }

        return Optional.ofNullable(objectMapper.readValue(trimmed, IrisPersonRestDto.class));
    }

    private Professor toProfessor(IrisPersonRestDto personDto, String codiceFiscale) {
        List<ExternalIdentifier> identifiers = new ArrayList<>();

        if (personDto.crisId() != null && !personDto.crisId().isBlank()) {
            identifiers.add(new ExternalIdentifier(
                    IdentifierType.IRIS_ID,
                    personDto.crisId(),
                    SourceType.IRIS
            ));
        }

        if (codiceFiscale != null && !codiceFiscale.isBlank()) {
            identifiers.add(new ExternalIdentifier(
                    IdentifierType.CODICE_FISCALE,
                    codiceFiscale,
                    SourceType.IRIS
            ));
        }

        return new Professor(
                personDto.firstName(),
                personDto.lastName(),
                buildFullName(personDto.firstName(), personDto.lastName()),
                "Università degli Studi di Cassino e del Lazio Meridionale",
                identifiers
        );
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

        List<IrisItemDto> allItems = fetchAllItemsByContextUser(crisId.get());

        System.out.println("IRIS items/search mapping started. crisId="
                + crisId.get()
                + ", rawItems="
                + allItems.size());

        List<Publication> publications = allItems.stream()
                .map(publicationMapper::toDomainPublication)
                .toList();

        System.out.println("IRIS items/search mapping completed. crisId="
                + crisId.get()
                + ", publications="
                + publications.size());

        return publications;
    }

    private List<IrisItemDto> fetchAllItemsByContextUser(String crisId) {
        List<IrisItemDto> accumulatedItems = new ArrayList<>();

        int offset = 0;
        int page = 0;
        Integer expectedTotal = null;

        while (page < MAX_ITEMS_PAGES) {
            IrisItemSearchResponseDto responseDto = fetchItemsPage(crisId, offset, ITEMS_PAGE_SIZE).orElse(null);

            if (responseDto == null) {
                System.out.println("IRIS items/search stopped. Empty or invalid response at offset=" + offset);
                break;
            }

            List<IrisItemDto> pageItems = responseDto.restResourceDtoList() != null
                    ? responseDto.restResourceDtoList()
                    : List.of();

            if (expectedTotal == null && responseDto.count() != null) {
                expectedTotal = responseDto.count();
            }

            System.out.println("IRIS items/search page fetched. crisId="
                    + crisId
                    + ", page="
                    + page
                    + ", offset="
                    + offset
                    + ", requestedLimit="
                    + ITEMS_PAGE_SIZE
                    + ", responseLimit="
                    + responseDto.limit()
                    + ", responseOffset="
                    + responseDto.offset()
                    + ", responseCount="
                    + responseDto.count()
                    + ", pageItems="
                    + pageItems.size()
                    + ", accumulatedBefore="
                    + accumulatedItems.size());

            if (pageItems.isEmpty()) {
                break;
            }

            accumulatedItems.addAll(pageItems);

            if (expectedTotal != null && accumulatedItems.size() >= expectedTotal) {
                break;
            }

            int nextOffset = calculateNextOffset(offset, responseDto, pageItems.size());

            if (nextOffset <= offset) {
                System.out.println("IRIS items/search stopped. Next offset did not advance. currentOffset="
                        + offset
                        + ", nextOffset="
                        + nextOffset);
                break;
            }

            offset = nextOffset;
            page++;
        }

        List<IrisItemDto> deduplicatedItems = deduplicateItemsKeepingOrder(accumulatedItems);

        System.out.println("IRIS items/search completed. crisId="
                + crisId
                + ", expectedTotal="
                + expectedTotal
                + ", accumulatedItems="
                + accumulatedItems.size()
                + ", deduplicatedItems="
                + deduplicatedItems.size());

        return deduplicatedItems;
    }

    private int calculateNextOffset(
            int currentOffset,
            IrisItemSearchResponseDto responseDto,
            int pageItemsSize
    ) {
        if (responseDto.offset() != null && pageItemsSize > 0) {
            return responseDto.offset() + pageItemsSize;
        }

        if (responseDto.limit() != null && responseDto.limit() > 0) {
            return currentOffset + responseDto.limit();
        }

        if (pageItemsSize > 0) {
            return currentOffset + pageItemsSize;
        }

        return currentOffset + ITEMS_PAGE_SIZE;
    }

    private Optional<IrisItemSearchResponseDto> fetchItemsPage(String crisId, int offset, int limit) {
        String jsonBody = buildItemsSearchBody(crisId, null, offset, limit);

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
                System.out.println("IRIS items/search page failed. crisId="
                        + crisId
                        + ", offset="
                        + offset
                        + ", status="
                        + response.statusCode()
                        + ", bodyPreview="
                        + preview(sanitizeForLog(response.body()), 300));
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(response.body(), IrisItemSearchResponseDto.class));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            System.out.println("IRIS items/search page exception. crisId="
                    + crisId
                    + ", offset="
                    + offset
                    + ", error="
                    + e.getClass().getSimpleName()
                    + ": "
                    + sanitizeForLog(e.getMessage()));
            return Optional.empty();
        }
    }

    private List<IrisItemDto> deduplicateItemsKeepingOrder(List<IrisItemDto> items) {
        Map<String, IrisItemDto> ordered = new LinkedHashMap<>();

        for (IrisItemDto item : items) {
            if (item == null) {
                continue;
            }

            String key = buildItemDeduplicationKey(item);
            ordered.putIfAbsent(key, item);
        }

        return new ArrayList<>(ordered.values());
    }

    private String buildItemDeduplicationKey(IrisItemDto item) {
        if (item.handle() != null && !item.handle().isBlank()) {
            return "handle:" + item.handle();
        }

        if (item.name() != null && !item.name().isBlank()) {
            return "name:" + item.name();
        }

        return "object:" + System.identityHashCode(item);
    }

    @Override
    public Optional<BibtexEntry> fetchBibtexEntry(Publication publication) {
        return Optional.empty();
    }

    private String buildItemsSearchBody(String crisId, String year, int offset, int limit) {
        if (year == null || year.isBlank()) {
            return """
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
                      "offset": %d,
                      "limit": %d,
                      "expand": "all",
                      "operator": "AND"
                    }
                    """.formatted(crisId, offset, limit);
        }

        return """
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
                  "offset": %d,
                  "limit": %d,
                  "expand": "all",
                  "operator": "AND"
                }
                """.formatted(crisId, year, offset, limit);
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
            HttpResponse<String> response = sendAuthenticatedGetResponse(url);
            return toResult(method, path, response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AuthenticatedRestCallResult(
                    method,
                    path,
                    -1,
                    null,
                    "",
                    "Chiamata interrotta"
            );
        } catch (IOException | IllegalArgumentException e) {
            return new AuthenticatedRestCallResult(
                    method,
                    path,
                    -1,
                    null,
                    "",
                    "Errore chiamata autenticata: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + sanitizeForLog(e.getMessage())
            );
        }
    }

    private HttpResponse<String> sendAuthenticatedGetResponse(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(authSettings.timeoutSeconds()))
                .header("Accept", "application/json, text/plain, */*")
                .header("Authorization", buildBasicAuthHeader())
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AuthenticatedRestCallResult(
                    method,
                    path,
                    -1,
                    null,
                    "",
                    "Chiamata interrotta"
            );
        } catch (IOException | IllegalArgumentException e) {
            return new AuthenticatedRestCallResult(
                    method,
                    path,
                    -1,
                    null,
                    "",
                    "Errore chiamata autenticata: "
                            + e.getClass().getSimpleName()
                            + " - "
                            + sanitizeForLog(e.getMessage())
            );
        }
    }

    private AuthenticatedRestCallResult toResult(String method, String path, HttpResponse<String> response) {
        String body = response.body() != null ? response.body() : "";
        String preview = buildAuthenticatedBodyPreview(path, body);
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
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
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

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    private String buildAuthenticatedBodyPreview(String path, String body) {
        if (path == null || path.isBlank()) {
            return "[omesso: risposta autenticata IRIS non stampata nei log]";
        }

        String normalizedPath = path.toLowerCase();

        if (normalizedPath.endsWith("/echo")) {
            return preview(sanitizeForLog(body), 300);
        }

        return "[omesso: risposta autenticata IRIS non stampata nei log]";
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("(?i)(authorization:\\s*basic\\s+)[a-z0-9+/=._-]+", "$1***")
                .replaceAll("(?i)(password=)[^&\\s\"']+", "$1***")
                .replaceAll("(?i)(token=)[^&\\s\"']+", "$1***")
                .replaceAll("(?i)(api_key=)[^&\\s\"']+", "$1***");
    }

    private String preview(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}