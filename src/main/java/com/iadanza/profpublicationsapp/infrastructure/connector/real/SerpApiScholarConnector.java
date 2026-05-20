package com.iadanza.profpublicationsapp.infrastructure.connector.real;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.domain.model.ScholarAuthorMapping;
import com.iadanza.profpublicationsapp.infrastructure.config.SerpApiScholarSettings;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScholarConnector;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connector reale per Google Scholar tramite SerpApi.
 *
 * Versione F2.4:
 * - recupera citation count Scholar;
 * - recupera documenti citanti Scholar tramite cites_id;
 * - segue la paginazione SerpApi senza imporre un limite massimo applicativo;
 * - evita scraping diretto custom di Google Scholar;
 * - degrada senza crash in caso di quota esaurita, errore HTTP o dati incompleti.
 *
 * Nota importante:
 * non c'è un limite massimo lato codice. Se una pubblicazione ha molte migliaia di citazioni,
 * il recupero completo può consumare molte query SerpApi. In caso di errore/quota, il connector
 * restituisce i documenti già raccolti fino a quel momento.
 */
public class SerpApiScholarConnector implements ScholarConnector {

    private static final String GOOGLE_SCHOLAR_ENGINE = "google_scholar";
    private static final int SCHOLAR_SEARCH_PAGE_SIZE = 10;
    private static final int SCHOLAR_CITING_DOCUMENTS_PAGE_SIZE = 20;

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");

    private final HttpClient httpClient;
    private final SerpApiScholarSettings settings;
    private final ObjectMapper objectMapper;

    public SerpApiScholarConnector(HttpClient httpClient, SerpApiScholarSettings settings) {
        this.httpClient = httpClient;
        this.settings = settings;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Optional<ScholarAuthorMapping> findScholarAuthorMapping(Professor professor) {
        /*
         * v1:
         * non facciamo discovery automatica del profilo Scholar.
         * Scholar viene interrogato a livello di singola pubblicazione.
         */
        return Optional.empty();
    }

    @Override
    public Optional<CitationSummary> fetchCitationSummary(Publication publication) {
        if (!isEnabled()) {
            System.out.println("SerpApi Scholar citation summary skipped. Reason: SERPAPI_API_KEY not configured.");
            return Optional.empty();
        }

        String query = buildScholarPublicationQuery(publication);

        if (query.isBlank()) {
            System.out.println("SerpApi Scholar citation summary skipped. Reason: insufficient publication metadata.");
            return Optional.empty();
        }

        try {
            System.out.println("SerpApi Scholar citation summary request started. query=" + abbreviate(query, 120));

            Optional<JsonNode> bestResult = searchBestScholarResult(query);

            if (bestResult.isEmpty()) {
                System.out.println("SerpApi Scholar citation summary not found. query=" + abbreviate(query, 120));
                return Optional.empty();
            }

            JsonNode result = bestResult.get();

            Integer scholarCitationCount = extractScholarCitationCount(result);
            String title = result.path("title").asText("");
            String resultId = result.path("result_id").asText("");
            String citesId = extractCitesId(result);

            if (scholarCitationCount == null) {
                System.out.println("SerpApi Scholar result found but citation count is missing. "
                        + "title="
                        + abbreviate(title, 90)
                        + ", resultId="
                        + resultId);
                return Optional.empty();
            }

            System.out.println("SerpApi Scholar citation summary fetched. "
                    + "title="
                    + abbreviate(title, 90)
                    + ", resultId="
                    + resultId
                    + ", citesId="
                    + safeLogValue(citesId)
                    + ", citedByCount="
                    + scholarCitationCount);

            return Optional.of(new CitationSummary(
                    null,
                    scholarCitationCount,
                    scholarCitationCount,
                    null,
                    null
            ));

        } catch (Exception e) {
            System.out.println("SerpApi Scholar citation summary error. "
                    + "query="
                    + abbreviate(query, 120)
                    + ", error="
                    + e.getClass().getSimpleName()
                    + ": "
                    + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<CitingDocument> findCitingDocuments(Publication publication) {
        if (!isEnabled()) {
            System.out.println("SerpApi Scholar citing documents skipped. Reason: SERPAPI_API_KEY not configured.");
            return List.of();
        }

        String query = buildScholarPublicationQuery(publication);

        if (query.isBlank()) {
            System.out.println("SerpApi Scholar citing documents skipped. Reason: insufficient publication metadata.");
            return List.of();
        }

        try {
            System.out.println("SerpApi Scholar citing documents lookup started. query=" + abbreviate(query, 120));

            Optional<JsonNode> bestResult = searchBestScholarResult(query);

            if (bestResult.isEmpty()) {
                System.out.println("SerpApi Scholar citing documents skipped. Reason: publication not found.");
                return List.of();
            }

            JsonNode result = bestResult.get();
            String citesId = extractCitesId(result);
            String title = result.path("title").asText("");

            if (citesId.isBlank()) {
                System.out.println("SerpApi Scholar citing documents skipped. Reason: cites_id missing. title="
                        + abbreviate(title, 90));
                return List.of();
            }

            List<CitingDocument> documents = fetchAllCitingDocumentsByCitesId(citesId);

            System.out.println("SerpApi Scholar citing documents fetched. "
                    + "sourceTitle="
                    + abbreviate(title, 90)
                    + ", citesId="
                    + citesId
                    + ", documents="
                    + documents.size());

            return documents;

        } catch (Exception e) {
            System.out.println("SerpApi Scholar citing documents error. "
                    + "query="
                    + abbreviate(query, 120)
                    + ", error="
                    + e.getClass().getSimpleName()
                    + ": "
                    + e.getMessage());
            return List.of();
        }
    }

    @Override
    public Optional<BibtexEntry> fetchBibtexEntry(Publication publication) {
        /*
         * v1:
         * Scholar/SerpApi viene usato per citation count e documenti citanti.
         * Il BibTeX resta gestito dal servizio BibTeX con fallback interno.
         */
        System.out.println("SerpApi Scholar BibTeX retrieval not implemented in v1. Returning empty result.");
        return Optional.empty();
    }

    private boolean isEnabled() {
        return settings != null && settings.isEnabled();
    }

    private Optional<JsonNode> searchBestScholarResult(String query)
            throws IOException, InterruptedException {

        URI uri = buildScholarSearchUri(query);
        HttpRequest request = buildRequest(uri);

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            logNonSuccessResponse("publication search", response.statusCode(), response.body());
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.body());

        if (root.hasNonNull("error")) {
            System.out.println("SerpApi Scholar publication search returned error: "
                    + root.path("error").asText());
            return Optional.empty();
        }

        JsonNode organicResults = root.path("organic_results");

        if (!organicResults.isArray() || organicResults.isEmpty()) {
            return Optional.empty();
        }

        JsonNode firstResult = organicResults.get(0);

        for (JsonNode result : organicResults) {
            if (hasCitedBy(result)) {
                return Optional.of(result);
            }
        }

        return Optional.of(firstResult);
    }

    private List<CitingDocument> fetchAllCitingDocumentsByCitesId(String citesId)
            throws IOException, InterruptedException {

        LinkedHashMap<String, CitingDocument> documentsByKey = new LinkedHashMap<>();

        URI currentUri = buildScholarCitingDocumentsUri(citesId);
        int page = 1;

        while (currentUri != null) {
            System.out.println("SerpApi Scholar citing documents page request started. "
                    + "citesId="
                    + citesId
                    + ", page="
                    + page
                    + ", accumulatedBefore="
                    + documentsByKey.size());

            HttpRequest request = buildRequest(currentUri);

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                logNonSuccessResponse("citing documents page " + page, response.statusCode(), response.body());
                break;
            }

            JsonNode root = objectMapper.readTree(response.body());

            if (root.hasNonNull("error")) {
                System.out.println("SerpApi Scholar citing documents page returned error. "
                        + "page="
                        + page
                        + ", error="
                        + root.path("error").asText());
                break;
            }

            JsonNode organicResults = root.path("organic_results");

            if (!organicResults.isArray() || organicResults.isEmpty()) {
                System.out.println("SerpApi Scholar citing documents page empty. page=" + page);
                break;
            }

            for (JsonNode result : organicResults) {
                CitingDocument document = mapScholarResultToCitingDocument(result);
                documentsByKey.putIfAbsent(buildCitingDocumentKey(document), document);
            }

            System.out.println("SerpApi Scholar citing documents page fetched. "
                    + "page="
                    + page
                    + ", pageItems="
                    + organicResults.size()
                    + ", accumulatedAfter="
                    + documentsByKey.size());

            String nextUrl = root.path("serpapi_pagination").path("next").asText("");

            if (nextUrl == null || nextUrl.isBlank()) {
                System.out.println("SerpApi Scholar citing documents pagination completed. pagesFetched=" + page);
                break;
            }

            currentUri = buildNextPageUri(nextUrl);
            page++;
        }

        return new ArrayList<>(documentsByKey.values());
    }

    private CitingDocument mapScholarResultToCitingDocument(JsonNode result) {
        String title = result.path("title").asText("N/D");
        String link = result.path("link").asText(null);

        JsonNode publicationInfo = result.path("publication_info");
        String publicationSummary = publicationInfo.path("summary").asText("");

        List<String> authors = extractAuthors(publicationSummary);
        Integer year = extractYear(publicationSummary);
        String doi = extractDoiFromLink(link);

        RecordStatus recordStatus = isCompleteEnough(title, authors, year, link)
                ? RecordStatus.COMPLETE
                : RecordStatus.PARTIAL_DATA;

        return new CitingDocument(
                title,
                authors,
                year,
                doi,
                SourceType.SCHOLAR,
                recordStatus,
                link
        );
    }

    private URI buildScholarSearchUri(String query) {
        String url = settings.baseUrl()
                + "?engine="
                + GOOGLE_SCHOLAR_ENGINE
                + "&q="
                + encode(query)
                + "&hl=en"
                + "&num="
                + SCHOLAR_SEARCH_PAGE_SIZE
                + "&api_key="
                + encode(settings.apiKey());

        return URI.create(url);
    }

    private URI buildScholarCitingDocumentsUri(String citesId) {
        String url = settings.baseUrl()
                + "?engine="
                + GOOGLE_SCHOLAR_ENGINE
                + "&cites="
                + encode(citesId)
                + "&hl=en"
                + "&num="
                + SCHOLAR_CITING_DOCUMENTS_PAGE_SIZE
                + "&api_key="
                + encode(settings.apiKey());

        return URI.create(url);
    }

    private URI buildNextPageUri(String nextUrl) {
        String url = nextUrl.trim();

        if (!url.contains("api_key=")) {
            String separator = url.contains("?") ? "&" : "?";
            url = url + separator + "api_key=" + encode(settings.apiKey());
        }

        return URI.create(url);
    }

    private HttpRequest buildRequest(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .GET()
                .header("Accept", "application/json")
                .build();
    }

    private String buildScholarPublicationQuery(Publication publication) {
        if (publication == null) {
            return "";
        }

        String title = publication.title() != null ? publication.title().trim() : "";
        String doi = publication.doi() != null ? publication.doi().trim() : "";

        StringBuilder builder = new StringBuilder();

        if (!title.isBlank()) {
            builder.append(title);
        }

        if (!doi.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append(" ");
            }

            builder.append(doi);
        }

        if (publication.authors() != null && !publication.authors().isEmpty()) {
            int maxAuthors = Math.min(publication.authors().size(), 4);

            for (int i = 0; i < maxAuthors; i++) {
                String author = publication.authors().get(i);

                if (author == null || author.isBlank()) {
                    continue;
                }

                if (!builder.isEmpty()) {
                    builder.append(" ");
                }

                builder.append(author);
            }
        }

        return builder.toString().trim();
    }

    private boolean hasCitedBy(JsonNode result) {
        return extractScholarCitationCount(result) != null || !extractCitesId(result).isBlank();
    }

    private Integer extractScholarCitationCount(JsonNode result) {
        String value = result
                .path("inline_links")
                .path("cited_by")
                .path("total")
                .asText("");

        if (value.isBlank()) {
            return null;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractCitesId(JsonNode result) {
        return result
                .path("inline_links")
                .path("cited_by")
                .path("cites_id")
                .asText("");
    }

    private List<String> extractAuthors(String publicationSummary) {
        if (publicationSummary == null || publicationSummary.isBlank()) {
            return List.of();
        }

        String authorsPart = publicationSummary;

        int separatorIndex = publicationSummary.indexOf(" - ");

        if (separatorIndex >= 0) {
            authorsPart = publicationSummary.substring(0, separatorIndex);
        }

        authorsPart = authorsPart
                .replace("…", "")
                .trim();

        if (authorsPart.isBlank()) {
            return List.of();
        }

        String[] rawAuthors = authorsPart.split(",");

        List<String> authors = new ArrayList<>();

        for (String rawAuthor : rawAuthors) {
            String author = rawAuthor.trim();

            if (!author.isBlank()) {
                authors.add(author);
            }
        }

        return authors;
    }

    private Integer extractYear(String publicationSummary) {
        if (publicationSummary == null || publicationSummary.isBlank()) {
            return null;
        }

        Matcher matcher = YEAR_PATTERN.matcher(publicationSummary);

        if (!matcher.find()) {
            return null;
        }

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractDoiFromLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }

        String lower = link.toLowerCase();

        int doiOrgIndex = lower.indexOf("doi.org/");

        if (doiOrgIndex >= 0) {
            return cleanDoi(link.substring(doiOrgIndex + "doi.org/".length()));
        }

        int doiPathIndex = lower.indexOf("/doi/");

        if (doiPathIndex >= 0) {
            return cleanDoi(link.substring(doiPathIndex + "/doi/".length()));
        }

        return null;
    }

    private String cleanDoi(String rawDoi) {
        if (rawDoi == null || rawDoi.isBlank()) {
            return null;
        }

        String cleaned = rawDoi.trim();

        int queryIndex = cleaned.indexOf('?');

        if (queryIndex >= 0) {
            cleaned = cleaned.substring(0, queryIndex);
        }

        int hashIndex = cleaned.indexOf('#');

        if (hashIndex >= 0) {
            cleaned = cleaned.substring(0, hashIndex);
        }

        return cleaned.isBlank() ? null : cleaned;
    }

    private boolean isCompleteEnough(String title, List<String> authors, Integer year, String link) {
        return title != null
                && !title.isBlank()
                && !"N/D".equalsIgnoreCase(title)
                && authors != null
                && !authors.isEmpty()
                && year != null
                && link != null
                && !link.isBlank();
    }

    private String buildCitingDocumentKey(CitingDocument document) {
        if (document == null) {
            return "";
        }

        String doi = document.doi() != null ? document.doi().trim().toLowerCase() : "";

        if (!doi.isBlank()) {
            return "doi:" + doi;
        }

        String url = document.sourceUrl() != null ? document.sourceUrl().trim().toLowerCase() : "";

        if (!url.isBlank()) {
            return "url:" + url;
        }

        String title = document.title() != null ? normalizeText(document.title()) : "";
        String year = document.year() != null ? document.year().toString() : "";
        String firstAuthor = "";

        if (document.authors() != null && !document.authors().isEmpty()) {
            firstAuthor = normalizeText(document.authors().get(0));
        }

        return "title-year-author:" + title + "|" + year + "|" + firstAuthor;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private void logNonSuccessResponse(String operation, int statusCode, String responseBody) {
        String preview = abbreviate(responseBody, 500);

        switch (statusCode) {
            case 400 -> System.out.println("SerpApi Scholar " + operation
                    + " rejected. status=400, bodyPreview=" + preview);

            case 401 -> System.out.println("SerpApi Scholar " + operation
                    + " unauthorized. status=401. Check SERPAPI_API_KEY.");

            case 403 -> System.out.println("SerpApi Scholar " + operation
                    + " forbidden. status=403. API key may not have quota or access.");

            case 429 -> System.out.println("SerpApi Scholar " + operation
                    + " rate limit/quota exceeded. status=429, bodyPreview=" + preview);

            default -> {
                if (statusCode >= 500) {
                    System.out.println("SerpApi Scholar " + operation
                            + " server error. status="
                            + statusCode
                            + ", bodyPreview="
                            + preview);
                } else {
                    System.out.println("SerpApi Scholar " + operation
                            + " unexpected HTTP status. status="
                            + statusCode
                            + ", bodyPreview="
                            + preview);
                }
            }
        }
    }

    private String safeLogValue(String value) {
        return value != null && !value.isBlank() ? value : "N/D";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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