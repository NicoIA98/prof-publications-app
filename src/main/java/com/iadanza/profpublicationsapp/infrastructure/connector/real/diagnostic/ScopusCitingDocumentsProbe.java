package com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iadanza.profpublicationsapp.infrastructure.config.ScopusApiSettings;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Probe diagnostico manuale per verificare se la API key Scopus
 * consente il recupero dei documenti citanti tramite EID.
 *
 * Uso da IntelliJ:
 * - tasto destro sulla classe
 * - Run 'ScopusCitingDocumentsProbe'
 *
 * Program argument opzionale:
 * 2-s2.0-84957895356
 *
 * Se non viene passato nessun EID, usa l'EID già verificato in E3:
 * DOI: 10.1016/j.ins.2016.01.021
 * citedbyCount: 23
 *
 * Nota:
 * non stampa mai API key o institutional token.
 */
public class ScopusCitingDocumentsProbe {

    private static final String SCOPUS_SEARCH_PATH = "/content/search/scopus";
    private static final String DEFAULT_TEST_EID = "2-s2.0-84957895356";
    private static final int DEFAULT_COUNT = 10;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        String eid = args.length > 0 && args[0] != null && !args[0].trim().isBlank()
                ? args[0].trim()
                : DEFAULT_TEST_EID;

        ScopusApiSettings settings = ScopusApiSettings.fromEnvironment();

        System.out.println("=== SCOPUS CITING DOCUMENTS PROBE ===");
        System.out.println("Scopus enabled: " + settings.isEnabled());
        System.out.println("Base URL: " + settings.baseUrl());
        System.out.println("Timeout seconds: " + settings.timeoutSeconds());
        System.out.println("Institutional token configured: " + settings.hasInstToken());
        System.out.println("Target EID: " + eid);
        System.out.println("Requested max citing documents: " + DEFAULT_COUNT);
        System.out.println("=====================================");

        if (!settings.isEnabled()) {
            System.out.println("Probe skipped. Reason: SCOPUS_API_KEY not configured.");
            return;
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .build();

        try {
            URI uri = buildCitingDocumentsUri(settings, eid, DEFAULT_COUNT);
            HttpRequest request = buildRequest(settings, uri);

            System.out.println("Scopus citing documents request started.");
            System.out.println("Query: REFEID(" + eid + ")");

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            System.out.println("HTTP status: " + response.statusCode());

            if (response.statusCode() != 200) {
                printNonSuccessResponse(response.statusCode(), response.body());
                return;
            }

            parseAndPrintResponse(response.body());

        } catch (java.net.http.HttpTimeoutException e) {
            System.out.println("Scopus citing documents timeout. timeoutSeconds="
                    + settings.timeoutSeconds());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Scopus citing documents probe interrupted.");

        } catch (IOException e) {
            System.out.println("Scopus citing documents IO error: "
                    + e.getClass().getSimpleName()
                    + " - "
                    + e.getMessage());

        } catch (Exception e) {
            System.out.println("Scopus citing documents unexpected error: "
                    + e.getClass().getSimpleName()
                    + " - "
                    + e.getMessage());
        }
    }

    private static URI buildCitingDocumentsUri(
            ScopusApiSettings settings,
            String eid,
            int count
    ) {
        String query = "REFEID(" + eid + ")";
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        String url = settings.baseUrl()
                + SCOPUS_SEARCH_PATH
                + "?query="
                + encodedQuery
                + "&count="
                + count
                + "&sort=-coverDate";

        return URI.create(url);
    }

    private static HttpRequest buildRequest(ScopusApiSettings settings, URI uri) {
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

    private static void parseAndPrintResponse(String responseBody) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(responseBody);
        JsonNode searchResults = root.path("search-results");

        String totalResults = searchResults.path("opensearch:totalResults").asText("0");
        String startIndex = searchResults.path("opensearch:startIndex").asText("0");
        String itemsPerPage = searchResults.path("opensearch:itemsPerPage").asText("0");

        System.out.println("Total citing documents reported by Scopus: " + totalResults);
        System.out.println("Start index: " + startIndex);
        System.out.println("Items per page: " + itemsPerPage);

        JsonNode entries = searchResults.path("entry");

        if (!entries.isArray() || entries.isEmpty()) {
            System.out.println("No citing documents returned in this response.");
            return;
        }

        System.out.println("-------------------------------------");

        int index = 1;

        for (JsonNode entry : entries) {
            String eid = entry.path("eid").asText("N/D");
            String title = entry.path("dc:title").asText("N/D");
            String creator = entry.path("dc:creator").asText("N/D");
            String doi = entry.path("prism:doi").asText("N/D");
            String coverDate = entry.path("prism:coverDate").asText("N/D");
            String publicationName = entry.path("prism:publicationName").asText("N/D");
            String citedByCount = entry.path("citedby-count").asText("N/D");
            String scopusUrl = extractScopusUrl(entry);

            System.out.println("Citing document #" + index);
            System.out.println("Title: " + title);
            System.out.println("Creator: " + creator);
            System.out.println("Year: " + extractYear(coverDate));
            System.out.println("Cover date: " + coverDate);
            System.out.println("Publication name: " + publicationName);
            System.out.println("DOI: " + doi);
            System.out.println("EID: " + eid);
            System.out.println("Cited-by count of citing document: " + citedByCount);
            System.out.println("Scopus URL: " + scopusUrl);
            System.out.println("Authors: " + extractAuthors(entry));
            System.out.println("-------------------------------------");

            index++;
        }
    }

    private static void printNonSuccessResponse(int statusCode, String responseBody) {
        String preview = abbreviate(responseBody, 500);

        switch (statusCode) {
            case 400 -> System.out.println("Request rejected by Scopus. status=400, bodyPreview=" + preview);
            case 401 -> System.out.println("Unauthorized. status=401. Check SCOPUS_API_KEY.");
            case 403 -> System.out.println("Forbidden. status=403. API key may not have access to REFEID/view=COMPLETE.");
            case 404 -> System.out.println("Endpoint or resource not found. status=404, bodyPreview=" + preview);
            case 429 -> System.out.println("Rate limit or quota exceeded. status=429, bodyPreview=" + preview);
            default -> {
                if (statusCode >= 500) {
                    System.out.println("Scopus server error. status=" + statusCode + ", bodyPreview=" + preview);
                } else {
                    System.out.println("Unexpected Scopus status. status=" + statusCode + ", bodyPreview=" + preview);
                }
            }
        }
    }

    private static String extractAuthors(JsonNode entry) {
        JsonNode authors = entry.path("author");

        if (!authors.isArray() || authors.isEmpty()) {
            String creator = entry.path("dc:creator").asText("");
            return creator.isBlank() ? "N/D" : creator;
        }

        StringBuilder builder = new StringBuilder();

        for (JsonNode author : authors) {
            String authName = author.path("authname").asText("");
            String givenName = author.path("given-name").asText("");
            String surname = author.path("surname").asText("");
            String initials = author.path("initials").asText("");

            String displayName;

            if (!authName.isBlank()) {
                displayName = authName;
            } else if (!givenName.isBlank() || !surname.isBlank()) {
                displayName = (givenName + " " + surname).trim();
            } else if (!surname.isBlank() || !initials.isBlank()) {
                displayName = (surname + " " + initials).trim();
            } else {
                displayName = "";
            }

            if (!displayName.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(", ");
                }
                builder.append(displayName);
            }
        }

        return builder.isEmpty() ? "N/D" : builder.toString();
    }

    private static String extractScopusUrl(JsonNode entry) {
        JsonNode links = entry.path("link");

        if (links.isArray()) {
            for (JsonNode link : links) {
                String ref = link.path("@ref").asText("");
                String href = link.path("@href").asText("");

                if ("scopus".equalsIgnoreCase(ref) && !href.isBlank()) {
                    return href;
                }
            }

            for (JsonNode link : links) {
                String href = link.path("@href").asText("");

                if (!href.isBlank() && href.contains("scopus")) {
                    return href;
                }
            }
        }

        String prismUrl = entry.path("prism:url").asText("");

        if (!prismUrl.isBlank()) {
            return prismUrl;
        }

        String eid = entry.path("eid").asText("");

        if (!eid.isBlank()) {
            return "https://www.scopus.com/record/display.uri?eid=" + eid;
        }

        return "N/D";
    }

    private static String extractYear(String coverDate) {
        if (coverDate == null || coverDate.isBlank() || coverDate.length() < 4) {
            return "N/D";
        }

        String year = coverDate.substring(0, 4);

        if (year.matches("\\d{4}")) {
            return year;
        }

        return "N/D";
    }

    private static String abbreviate(String value, int maxLength) {
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