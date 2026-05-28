package com.iadanza.profpublicationsapp.bootstrap;

import com.iadanza.profpublicationsapp.application.service.BibtexService;
import com.iadanza.profpublicationsapp.application.service.CitationService;
import com.iadanza.profpublicationsapp.application.service.ProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.PublicationCatalogService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultBibtexService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultCitationService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultProfessorSearchService;
import com.iadanza.profpublicationsapp.application.service.impl.DefaultPublicationCatalogService;
import com.iadanza.profpublicationsapp.domain.model.BibtexEntry;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Professor;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.domain.model.ScholarAuthorMapping;
import com.iadanza.profpublicationsapp.infrastructure.config.ConnectionSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.FileLocalSettingsRepository;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisAccessMode;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisRestAuthSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.IrisRuntimeSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.LocalSettingsRepository;
import com.iadanza.profpublicationsapp.infrastructure.config.ScopusApiSettings;
import com.iadanza.profpublicationsapp.infrastructure.config.SerpApiScholarSettings;
import com.iadanza.profpublicationsapp.infrastructure.connector.IrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScholarConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.ScopusConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.RealIrisConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.RealScopusConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.SerpApiScholarConnector;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic.AuthenticatedRestCallResult;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic.IrisRestAdvancedProbe;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic.RestEndpointProbeResult;
import com.iadanza.profpublicationsapp.infrastructure.lookup.CsvProfessorLookupRepository;
import com.iadanza.profpublicationsapp.infrastructure.lookup.ProfessorLookupRepository;
import com.iadanza.profpublicationsapp.infrastructure.persistence.CitationCacheRepository;
import com.iadanza.profpublicationsapp.infrastructure.persistence.PublicationCacheRepository;
import com.iadanza.profpublicationsapp.infrastructure.persistence.SqliteCitationCacheRepository;
import com.iadanza.profpublicationsapp.infrastructure.persistence.SqlitePublicationCacheRepository;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;

/**
 * Bootstrap applicativo.
 *
 * Responsabilità:
 * - costruire connector reali oppure connector non disponibili quando mancano le API key;
 * - leggere configurazioni locali da settings.properties;
 * - usare .env / variabili d'ambiente come fallback;
 * - creare repository SQLite;
 * - creare application services;
 * - restituire alla UI un oggetto AppServices già pronto.
 *
 * Ordine configurazione:
 * 1. settings.properties locale in user.home/.prof-publications-app;
 * 2. variabili d'ambiente / .env;
 * 3. default applicativi.
 *
 * Nota demo/finale:
 * - i connector fake non vengono più usati;
 * - IRIS usa direttamente RealIrisConnector;
 * - se Scopus o SerpApi non sono configurati, l'app restituisce dati vuoti
 *   e non inventa citazioni, documenti citanti o BibTeX.
 */
public final class AppBootstrap {

    private AppBootstrap() {
    }

    public static AppServices createServices() {
        LocalSettingsRepository localSettingsRepository = new FileLocalSettingsRepository();
        boolean localSettingsFileExists = localSettingsRepository.exists();
        ConnectionSettings connectionSettings = loadConnectionSettings(localSettingsRepository);

        System.out.println("Local settings path: " + localSettingsRepository.getSettingsPath());
        System.out.println("Local settings file found: " + localSettingsFileExists);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(15))
                .build();

        IrisRuntimeSettings irisRuntimeSettings = new IrisRuntimeSettings(
                "https://iris.unicas.it",
                IrisAccessMode.AUTO,
                "/api/discover/search/objects",
                "/oai/request?verb=Identify",
                15,
                true
        );

        IrisRestAuthSettings irisRestAuthSettings =
                IrisRestAuthSettings.fromLocalSettingsWithEnvironmentFallback(
                        connectionSettings,
                        localSettingsFileExists
                );

        System.out.println("IRIS REST credentials loaded. Username configured: "
                + hasText(irisRestAuthSettings.username())
                + ", password configured: "
                + hasText(irisRestAuthSettings.password()));

        RealIrisConnector realIrisConnector =
                new RealIrisConnector(httpClient, irisRuntimeSettings, irisRestAuthSettings);

        printIrisProbe(realIrisConnector);
        printIrisAdvancedProbe();
        printIrisAuthenticatedTests(realIrisConnector);

        IrisConnector irisConnector = realIrisConnector;

        ScopusApiSettings scopusApiSettings =
                ScopusApiSettings.fromLocalSettingsWithEnvironmentFallback(
                        connectionSettings,
                        localSettingsFileExists
                );

        ScopusConnector scopusConnector = createScopusConnector(scopusApiSettings);

        SerpApiScholarSettings serpApiScholarSettings =
                SerpApiScholarSettings.fromLocalSettingsWithEnvironmentFallback(
                        connectionSettings,
                        localSettingsFileExists
                );

        ScholarConnector scholarConnector = createScholarConnector(serpApiScholarSettings);

        PublicationCacheRepository publicationCacheRepository =
                new SqlitePublicationCacheRepository("jdbc:sqlite:prof-publications.db");

        CitationCacheRepository citationCacheRepository =
                new SqliteCitationCacheRepository("jdbc:sqlite:prof-publications.db");

        ProfessorSearchService professorSearchService =
                new DefaultProfessorSearchService(irisConnector);

        PublicationCatalogService publicationCatalogService =
                new DefaultPublicationCatalogService(irisConnector, publicationCacheRepository);

        CitationService citationService =
                new DefaultCitationService(scopusConnector, scholarConnector, citationCacheRepository);

        BibtexService bibtexService =
                new DefaultBibtexService(irisConnector, scopusConnector, scholarConnector);

        ProfessorLookupRepository professorLookupRepository =
                new CsvProfessorLookupRepository("/lookup/professors-cf.csv");

        return new AppServices(
                professorSearchService,
                publicationCatalogService,
                citationService,
                bibtexService,
                professorLookupRepository,
                localSettingsRepository
        );
    }

    private static ConnectionSettings loadConnectionSettings(LocalSettingsRepository localSettingsRepository) {
        try {
            return localSettingsRepository.load();
        } catch (IOException e) {
            System.out.println("Local settings could not be loaded. Falling back to .env/environment.");
            return ConnectionSettings.empty();
        }
    }

    private static ScopusConnector createScopusConnector(ScopusApiSettings scopusApiSettings) {
        if (scopusApiSettings == null || !scopusApiSettings.isEnabled()) {
            System.out.println("Scopus real connector unavailable. SCOPUS_API_KEY not configured.");
            return new UnavailableScopusConnector();
        }

        HttpClient scopusHttpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(scopusApiSettings.timeoutSeconds()))
                .build();

        System.out.println("Scopus real connector enabled. "
                + "baseUrl="
                + scopusApiSettings.baseUrl()
                + ", timeoutSeconds="
                + scopusApiSettings.timeoutSeconds()
                + ", instTokenConfigured="
                + scopusApiSettings.hasInstToken());

        return new RealScopusConnector(scopusHttpClient, scopusApiSettings);
    }

    private static ScholarConnector createScholarConnector(SerpApiScholarSettings serpApiScholarSettings) {
        if (serpApiScholarSettings == null || !serpApiScholarSettings.isEnabled()) {
            System.out.println("SerpApi Scholar connector unavailable. SERPAPI_API_KEY not configured.");
            return new UnavailableScholarConnector();
        }

        HttpClient scholarHttpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(serpApiScholarSettings.timeoutSeconds()))
                .build();

        System.out.println("SerpApi Scholar connector enabled. "
                + "baseUrl="
                + serpApiScholarSettings.baseUrl()
                + ", timeoutSeconds="
                + serpApiScholarSettings.timeoutSeconds());

        return new SerpApiScholarConnector(scholarHttpClient, serpApiScholarSettings);
    }

    private static void printIrisProbe(RealIrisConnector realIrisConnector) {
        System.out.println("=== IRIS REAL PROBE ===");
        System.out.println("Base URL: " + realIrisConnector.getProbeResult().baseUrl());
        System.out.println("REST status: " + realIrisConnector.getProbeResult().restStatusCode());
        System.out.println("OAI status: " + realIrisConnector.getProbeResult().oaiStatusCode());
        System.out.println("REST supported: " + realIrisConnector.getProbeResult().restSupported());
        System.out.println("OAI supported: " + realIrisConnector.getProbeResult().oaiSupported());
        System.out.println("Capabilities: " + realIrisConnector.getProbeResult().capabilities());
        System.out.println("Notes: " + realIrisConnector.getProbeResult().notes());
        System.out.println("=======================");
    }

    private static void printIrisAdvancedProbe() {
        IrisRestAdvancedProbe irisRestAdvancedProbe =
                new IrisRestAdvancedProbe(
                        HttpClient.newBuilder()
                                .connectTimeout(java.time.Duration.ofSeconds(15))
                                .followRedirects(HttpClient.Redirect.NEVER)
                                .build(),
                        "https://iris.unicas.it"
                );

        System.out.println("=== IRIS REST ADVANCED PROBE ===");
        for (RestEndpointProbeResult result : irisRestAdvancedProbe.probeAll()) {
            System.out.println("Method: " + result.method());
            System.out.println("Path: " + result.path());
            System.out.println("Status: " + result.statusCode());
            System.out.println("Redirected: " + result.redirected());
            System.out.println("Auth likely required: " + result.authLikelyRequired());
            System.out.println("Endpoint exists likely: " + result.endpointExistsLikely());
            System.out.println("Location: " + result.locationHeader());
            System.out.println("Content-Type: " + result.contentType());
            System.out.println("Notes: " + result.notes());
            System.out.println("Body preview: " + result.bodyPreview());
            System.out.println("--------------------------------");
        }
        System.out.println("================================");
    }

    private static void printIrisAuthenticatedTests(RealIrisConnector realIrisConnector) {
        System.out.println("=== IRIS AUTHENTICATED REST TESTS ===");
        printAuthenticatedResult(realIrisConnector.probeAuthenticatedIrEcho());
        printAuthenticatedResult(realIrisConnector.probeAuthenticatedRmEcho());
        printAuthenticatedResult(realIrisConnector.probeAuthenticatedPersonByCrisId("rp00418"));
        printAuthenticatedResult(realIrisConnector.probeAuthenticatedItemsByContextUser("rp00418"));
        printAuthenticatedResult(realIrisConnector.probeAuthenticatedItemsByContextUserAndYear("rp00418", "2024"));
        System.out.println("=====================================");
    }

    private static void printAuthenticatedResult(AuthenticatedRestCallResult result) {
        System.out.println("Method: " + result.method());
        System.out.println("Path: " + result.path());
        System.out.println("Status: " + result.statusCode());
        System.out.println("Content-Type: " + result.contentType());
        System.out.println("Notes: " + result.notes());
        System.out.println("Body preview: " + result.bodyPreview());
        System.out.println("--------------------------------");
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    /**
     * Connector Scopus non disponibile.
     *
     * Non è un fake: non simula citazioni, documenti citanti o BibTeX.
     * Serve solo a mantenere l'app avviabile quando manca SCOPUS_API_KEY.
     */
    private static final class UnavailableScopusConnector implements ScopusConnector {

        @Override
        public Optional<CitationSummary> fetchCitationSummary(Publication publication) {
            System.out.println("Scopus citation summary skipped. SCOPUS_API_KEY not configured.");
            return Optional.empty();
        }

        @Override
        public List<CitingDocument> findCitingDocuments(Publication publication) {
            System.out.println("Scopus citing documents skipped. SCOPUS_API_KEY not configured.");
            return List.of();
        }

        @Override
        public Optional<BibtexEntry> fetchBibtexEntry(Publication publication) {
            System.out.println("Scopus BibTeX lookup skipped. SCOPUS_API_KEY not configured.");
            return Optional.empty();
        }
    }

    /**
     * Connector Scholar non disponibile.
     *
     * Non è un fake: non simula citazioni, documenti citanti o BibTeX.
     * Serve solo a mantenere l'app avviabile quando manca SERPAPI_API_KEY.
     */
    private static final class UnavailableScholarConnector implements ScholarConnector {

        @Override
        public Optional<ScholarAuthorMapping> findScholarAuthorMapping(Professor professor) {
            System.out.println("Scholar author mapping skipped. SERPAPI_API_KEY not configured.");
            return Optional.empty();
        }

        @Override
        public Optional<CitationSummary> fetchCitationSummary(Publication publication) {
            System.out.println("Scholar citation summary skipped. SERPAPI_API_KEY not configured.");
            return Optional.empty();
        }

        @Override
        public List<CitingDocument> findCitingDocuments(Publication publication) {
            System.out.println("Scholar citing documents skipped. SERPAPI_API_KEY not configured.");
            return List.of();
        }

        @Override
        public Optional<BibtexEntry> fetchBibtexEntry(Publication publication) {
            System.out.println("Scholar BibTeX lookup skipped. SERPAPI_API_KEY not configured.");
            return Optional.empty();
        }
    }
}