package com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iadanza.profpublicationsapp.infrastructure.config.SerpApiScholarSettings;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Probe diagnostico per Google Scholar tramite SerpApi.
 *
 * Obiettivi:
 * - verificare che SERPAPI_API_KEY funzioni;
 * - cercare una pubblicazione Scholar tramite titolo/DOI;
 * - stampare tutti i risultati della prima pagina;
 * - scegliere il primo risultato con inline_links.cited_by / cites_id disponibile;
 * - provare il recupero dei documenti citanti tramite parametro cites.
 *
 * Uso da IntelliJ:
 * - tasto destro sulla classe
 * - Run 'SerpApiScholarProbe'
 *
 * Program arguments opzionali:
 * - nessuno: usa una pubblicazione di test già nota;
 * - query manuale: usa quel testo come query Scholar;
 * - --cites 123456789: prova direttamente i documenti citanti.
 *
 * Nota:
 * non stampa mai la API key.
 */
public class SerpApiScholarProbe {

    private static final String DEFAULT_QUERY =
            "An effective learning strategy for cascaded object detection Bria Marrocco Molinara Tortorella";

    private static final int FIRST_PAGE_NUM = 10;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        SerpApiScholarSettings settings = SerpApiScholarSettings.fromEnvironment();

        System.out.println("=== SERPAPI SCHOLAR PROBE ===");
        System.out.println("SerpApi enabled: " + settings.isEnabled());
        System.out.println("Base URL: " + settings.baseUrl());
        System.out.println("Timeout seconds: " + settings.timeoutSeconds());
        System.out.println("=============================");

        if (!settings.isEnabled()) {
            System.out.println("Probe skipped. Reason: SERPAPI_API_KEY not configured.");
            return;
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .build();

        try {
            if (args.length >= 2 && "--cites".equalsIgnoreCase(args[0])) {
                String citesId = args[1].trim();
                runCitingDocumentsProbe(httpClient, settings, citesId);
                return;
            }

            String query = buildQueryFromArgs(args);
            runPublicationSearchProbe(httpClient, settings, query);

        } catch (java.net.http.HttpTimeoutException e) {
            System.out.println("SerpApi Scholar timeout. timeoutSeconds=" + settings.timeoutSeconds());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("SerpApi Scholar probe interrupted.");

        } catch (IOException e) {
            System.out.println("SerpApi Scholar IO error: "
                    + e.getClass().getSimpleName()
                    + " - "
                    + e.getMessage());

        } catch (Exception e) {
            System.out.println("SerpApi Scholar unexpected error: "
                    + e.getClass().getSimpleName()
                    + " - "
                    + e.getMessage());
        }
    }

    private static void runPublicationSearchProbe(
            HttpClient httpClient,
            SerpApiScholarSettings settings,
            String query
    ) throws IOException, InterruptedException {

        System.out.println("Scholar publication search started.");
        System.out.println("Query: " + query);

        URI uri = buildScholarSearchUri(settings, query);
        HttpRequest request = buildRequest(settings, uri);

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("HTTP status: " + response.statusCode());

        if (response.statusCode() != 200) {
            printNonSuccessResponse(response.statusCode(), response.body());
            return;
        }

        JsonNode root = OBJECT_MAPPER.readTree(response.body());

        if (root.hasNonNull("error")) {
            System.out.println("SerpApi returned error: " + root.path("error").asText());
            return;
        }

        JsonNode organicResults = root.path("organic_results");

        if (!organicResults.isArray() || organicResults.isEmpty()) {
            System.out.println("No Scholar organic results found.");
            return;
        }

        System.out.println("Scholar organic results in first page: " + organicResults.size());
        System.out.println("-------------------------------------");

        JsonNode bestWithCites = null;

        for (int i = 0; i < organicResults.size(); i++) {
            JsonNode result = organicResults.get(i);
            printScholarResult("Scholar result #" + (i + 1), result);

            if (bestWithCites == null && hasCitesId(result)) {
                bestWithCites = result;
            }
        }

        if (bestWithCites == null) {
            System.out.println("No result with cites_id found in first page.");
            System.out.println("Citing documents probe skipped.");
            return;
        }

        System.out.println("Selected result with cites_id:");
        printScholarResult("Selected Scholar result", bestWithCites);

        String citesId = extractCitesId(bestWithCites);

        System.out.println("Now testing first page of citing documents...");
        System.out.println("-------------------------------------");

        runCitingDocumentsProbe(httpClient, settings, citesId);
    }

    private static void runCitingDocumentsProbe(
            HttpClient httpClient,
            SerpApiScholarSettings settings,
            String citesId
    ) throws IOException, InterruptedException {

        if (citesId == null || citesId.isBlank()) {
            System.out.println("Citing documents probe skipped. Reason: empty cites_id.");
            return;
        }

        System.out.println("Scholar citing documents search started.");
        System.out.println("cites=" + citesId);

        URI uri = buildScholarCitingDocumentsUri(settings, citesId);
        HttpRequest request = buildRequest(settings, uri);

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        System.out.println("HTTP status: " + response.statusCode());

        if (response.statusCode() != 200) {
            printNonSuccessResponse(response.statusCode(), response.body());
            return;
        }

        JsonNode root = OBJECT_MAPPER.readTree(response.body());

        if (root.hasNonNull("error")) {
            System.out.println("SerpApi returned error: " + root.path("error").asText());
            return;
        }

        JsonNode searchInformation = root.path("search_information");
        String totalResults = searchInformation.path("total_results").asText("N/D");

        System.out.println("Scholar citing documents total results: " + totalResults);

        JsonNode organicResults = root.path("organic_results");

        if (!organicResults.isArray() || organicResults.isEmpty()) {
            System.out.println("No citing documents returned in first page.");
            return;
        }

        System.out.println("Citing documents returned in first page: " + organicResults.size());
        System.out.println("-------------------------------------");

        for (int i = 0; i < organicResults.size(); i++) {
            JsonNode result = organicResults.get(i);
            printScholarResult("Citing document #" + (i + 1), result);
        }

        JsonNode pagination = root.path("serpapi_pagination");
        String next = pagination.path("next").asText("");

        if (!next.isBlank()) {
            System.out.println("More citing documents pages are available through SerpApi pagination.");
        } else {
            System.out.println("No further citing documents page reported by SerpApi.");
        }
    }

    private static URI buildScholarSearchUri(
            SerpApiScholarSettings settings,
            String query
    ) {
        String url = settings.baseUrl()
                + "?engine=google_scholar"
                + "&q="
                + encode(query)
                + "&hl=en"
                + "&num="
                + FIRST_PAGE_NUM
                + "&api_key="
                + encode(settings.apiKey());

        return URI.create(url);
    }

    private static URI buildScholarCitingDocumentsUri(
            SerpApiScholarSettings settings,
            String citesId
    ) {
        String url = settings.baseUrl()
                + "?engine=google_scholar"
                + "&cites="
                + encode(citesId)
                + "&hl=en"
                + "&num="
                + FIRST_PAGE_NUM
                + "&api_key="
                + encode(settings.apiKey());

        return URI.create(url);
    }

    private static HttpRequest buildRequest(SerpApiScholarSettings settings, URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .GET()
                .header("Accept", "application/json")
                .build();
    }

    private static void printScholarResult(String label, JsonNode result) {
        String title = result.path("title").asText("N/D");
        String resultId = result.path("result_id").asText("N/D");
        String link = result.path("link").asText("N/D");
        String snippet = result.path("snippet").asText("N/D");

        JsonNode publicationInfo = result.path("publication_info");
        String publicationSummary = publicationInfo.path("summary").asText("N/D");

        JsonNode inlineLinks = result.path("inline_links");
        JsonNode citedBy = inlineLinks.path("cited_by");

        String citedByTotal = citedBy.path("total").asText("N/D");
        String citesId = citedBy.path("cites_id").asText("N/D");
        String citedByLink = citedBy.path("link").asText("N/D");
        String serpApiCiteLink = inlineLinks.path("serpapi_cite_link").asText("N/D");

        System.out.println(label);
        System.out.println("Title: " + title);
        System.out.println("Result ID: " + resultId);
        System.out.println("Publication info: " + publicationSummary);
        System.out.println("Scholar citation count: " + citedByTotal);
        System.out.println("Cites ID: " + citesId);
        System.out.println("Cited by link: " + citedByLink);
        System.out.println("SerpApi cite link: " + serpApiCiteLink);
        System.out.println("Link: " + link);
        System.out.println("Snippet: " + abbreviate(snippet, 300));
        System.out.println("-------------------------------------");
    }

    private static boolean hasCitesId(JsonNode result) {
        return !extractCitesId(result).isBlank();
    }

    private static String extractCitesId(JsonNode result) {
        JsonNode citedBy = result.path("inline_links").path("cited_by");
        String citesId = citedBy.path("cites_id").asText("");

        if (!citesId.isBlank()) {
            return citesId;
        }

        return "";
    }

    private static String buildQueryFromArgs(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_QUERY;
        }

        StringBuilder builder = new StringBuilder();

        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(" ");
            }

            builder.append(arg.trim());
        }

        String query = builder.toString().trim();

        if (query.isBlank()) {
            return DEFAULT_QUERY;
        }

        return query;
    }

    private static void printNonSuccessResponse(int statusCode, String responseBody) {
        String preview = abbreviate(responseBody, 500);

        switch (statusCode) {
            case 400 -> System.out.println("Request rejected by SerpApi. status=400, bodyPreview=" + preview);
            case 401 -> System.out.println("Unauthorized. status=401. Check SERPAPI_API_KEY.");
            case 403 -> System.out.println("Forbidden. status=403. SerpApi key may not have access or quota.");
            case 429 -> System.out.println("Rate limit or quota exceeded. status=429, bodyPreview=" + preview);
            default -> {
                if (statusCode >= 500) {
                    System.out.println("SerpApi server error. status=" + statusCode + ", bodyPreview=" + preview);
                } else {
                    System.out.println("Unexpected SerpApi status. status=" + statusCode + ", bodyPreview=" + preview);
                }
            }
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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