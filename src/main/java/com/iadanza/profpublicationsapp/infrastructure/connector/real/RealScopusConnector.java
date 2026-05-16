package com.iadanza.profpublicationsapp.infrastructure.connector.real;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.config.ScopusApiSettings;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScopusConnector;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Connettore reale minimale per Scopus / Elsevier.
 *
 * Fase E2 — Scopus reale minima:
 * - recupera il numero di citazioni Scopus a partire dal DOI;
 * - usa le API ufficiali Elsevier;
 * - legge configurazione da ScopusApiSettings;
 * - degrada senza crash se API key, DOI o accesso Scopus non sono disponibili;
 * - non stampa mai API key o institutional token nei log.
 *
 * Nota:
 * In questa fase NON implementiamo ancora:
 * - documenti citanti reali;
 * - BibTeX reale da Scopus.
 */
public class RealScopusConnector implements ScopusConnector {

    private static final String SCOPUS_SEARCH_PATH = "/content/search/scopus";

    private final HttpClient httpClient;
    private final ScopusApiSettings settings;
    private final ObjectMapper objectMapper;

    public RealScopusConnector(HttpClient httpClient, ScopusApiSettings settings) {
        this.httpClient = httpClient;
        this.settings = settings;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Optional<CitationSummary> fetchCitationSummary(Publication publication) {
        if (publication == null) {
            System.out.println("Scopus citation summary skipped. Reason: publication is null.");
            return Optional.empty();
        }

        if (settings == null || !settings.isEnabled()) {
            System.out.println("Scopus citation summary skipped. Reason: SCOPUS_API_KEY not configured.");
            return Optional.empty();
        }

        String doi = normalizeDoi(publication.doi());

        if (doi.isBlank()) {
            System.out.println("Scopus citation summary skipped. Reason: publication has no DOI. title="
                    + safeTitle(publication));
            return Optional.empty();
        }

        try {
            URI uri = buildSearchByDoiUri(doi);
            HttpRequest request = buildRequest(uri);

            System.out.println("Scopus citation summary request started. doi=" + doi);

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            int statusCode = response.statusCode();

            if (statusCode == 200) {
                return parseCitationSummary(response.body(), doi);
            }

            handleNonSuccessStatus(statusCode, doi, response.body());
            return Optional.empty();

        } catch (java.net.http.HttpTimeoutException e) {
            System.out.println("Scopus citation summary timeout. doi=" + doi
                    + ", timeoutSeconds=" + settings.timeoutSeconds());
            return Optional.empty();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Scopus citation summary interrupted. doi=" + doi);
            return Optional.empty();

        } catch (IOException e) {
            System.out.println("Scopus citation summary IO error. doi=" + doi
                    + ", error=" + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return Optional.empty();

        } catch (Exception e) {
            System.out.println("Scopus citation summary unexpected error. doi=" + doi
                    + ", error=" + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<CitingDocument> findCitingDocuments(Publication publication) {
        System.out.println("Scopus citing documents not implemented in E2. Returning empty list.");
        return List.of();
    }

    @Override
    public Optional<BibtexEntry> fetchBibtexEntry(Publication publication) {
        System.out.println("Scopus BibTeX retrieval not implemented in E2. Returning empty result.");
        return Optional.empty();
    }

    private HttpRequest buildRequest(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .GET()
                .header("Accept", "application/json")
                .header("X-ELS-APIKey", settings.apiKey());

        if (settings.hasInstToken()) {
            builder.header("X-ELS-Insttoken", settings.instToken());
        }

        return builder.build();
    }

    private URI buildSearchByDoiUri(String doi) {
        String query = "DOI(" + doi + ")";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        String url = settings.baseUrl()
                + SCOPUS_SEARCH_PATH
                + "?query="
                + encodedQuery
                + "&count=1";

        return URI.create(url);
    }

    private Optional<CitationSummary> parseCitationSummary(String responseBody, String requestedDoi)
            throws IOException {

        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode searchResults = root.path("search-results");

        int totalResults = parseInteger(searchResults.path("opensearch:totalResults").asText(null), 0);

        if (totalResults <= 0) {
            System.out.println("Scopus citation summary not found. doi=" + requestedDoi
                    + ", totalResults=" + totalResults);
            return Optional.empty();
        }

        JsonNode entries = searchResults.path("entry");

        if (!entries.isArray() || entries.isEmpty()) {
            System.out.println("Scopus citation summary empty entries. doi=" + requestedDoi);
            return Optional.empty();
        }

        JsonNode firstEntry = entries.get(0);

        Integer citedByCount = parseNullableInteger(firstEntry.path("citedby-count").asText(null));
        String eid = firstEntry.path("eid").asText("");
        String returnedDoi = firstEntry.path("prism:doi").asText("");
        String title = firstEntry.path("dc:title").asText("");

        if (citedByCount == null) {
            System.out.println("Scopus citation summary found but citedby-count is missing. doi="
                    + requestedDoi
                    + ", eid="
                    + eid);
            return Optional.empty();
        }

        System.out.println("Scopus citation summary fetched. requestedDoi="
                + requestedDoi
                + ", returnedDoi="
                + returnedDoi
                + ", eid="
                + eid
                + ", title="
                + abbreviate(title, 90)
                + ", citedbyCount="
                + citedByCount);

        return Optional.of(new CitationSummary(
                citedByCount,
                null,
                citedByCount
        ));
    }

    private void handleNonSuccessStatus(int statusCode, String doi, String responseBody) {
        String preview = abbreviate(responseBody, 220);

        switch (statusCode) {
            case 400 -> System.out.println("Scopus request rejected. status=400, doi="
                    + doi + ", bodyPreview=" + preview);

            case 401 -> System.out.println("Scopus unauthorized. status=401, doi="
                    + doi + ". Check SCOPUS_API_KEY.");

            case 403 -> System.out.println("Scopus forbidden. status=403, doi="
                    + doi + ". API key may not have Scopus entitlement.");

            case 404 -> System.out.println("Scopus endpoint/resource not found. status=404, doi="
                    + doi + ", bodyPreview=" + preview);

            case 429 -> System.out.println("Scopus rate limit or quota exceeded. status=429, doi="
                    + doi + ", bodyPreview=" + preview);

            default -> {
                if (statusCode >= 500) {
                    System.out.println("Scopus server error. status="
                            + statusCode
                            + ", doi="
                            + doi
                            + ", bodyPreview="
                            + preview);
                } else {
                    System.out.println("Scopus unexpected HTTP status. status="
                            + statusCode
                            + ", doi="
                            + doi
                            + ", bodyPreview="
                            + preview);
                }
            }
        }
    }

    private String normalizeDoi(String doi) {
        if (doi == null) {
            return "";
        }

        String normalized = doi.trim();

        if (normalized.toLowerCase().startsWith("https://doi.org/")) {
            normalized = normalized.substring("https://doi.org/".length());
        }

        if (normalized.toLowerCase().startsWith("http://doi.org/")) {
            normalized = normalized.substring("http://doi.org/".length());
        }

        if (normalized.toLowerCase().startsWith("doi:")) {
            normalized = normalized.substring("doi:".length());
        }

        return normalized.trim();
    }

    private Integer parseNullableInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseInteger(String value, int defaultValue) {
        Integer parsed = parseNullableInteger(value);
        return parsed != null ? parsed : defaultValue;
    }

    private String safeTitle(Publication publication) {
        if (publication.title() == null || publication.title().isBlank()) {
            return "N/D";
        }

        return abbreviate(publication.title(), 90);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null) {
            return "";
        }

        String normalized = value.replace("\n", " ").replace("\r", " ").trim();

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}