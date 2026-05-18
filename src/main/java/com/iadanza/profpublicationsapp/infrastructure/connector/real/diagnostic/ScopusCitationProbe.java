package com.iadanza.profpublicationsapp.infrastructure.connector.real.diagnostic;

import com.iadanza.profpublicationsapp.domain.enums.RecordStatus;
import com.iadanza.profpublicationsapp.domain.enums.SourceType;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.Publication;
import com.iadanza.profpublicationsapp.infrastructure.config.ScopusApiSettings;
import com.iadanza.profpublicationsapp.infrastructure.connector.real.RealScopusConnector;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Probe diagnostico manuale per testare Scopus reale su un singolo DOI.
 *
 * Uso da IntelliJ:
 * - tasto destro su questa classe
 * - Run 'ScopusCitationProbe'
 * - inserire il DOI come Program argument
 *
 * Esempi Program arguments:
 * 10.1016/j.ins.2016.01.021
 * 10.1000/test
 * --no-doi
 * --blank-doi
 *
 * Nota:
 * non stampa mai API key o token.
 */
public class ScopusCitationProbe {

    private static final String NO_DOI_ARGUMENT = "--no-doi";
    private static final String BLANK_DOI_ARGUMENT = "--blank-doi";

    public static void main(String[] args) {
        String argument = args.length > 0 ? args[0].trim() : "";

        if (argument.isBlank()) {
            System.out.println("Scopus citation probe skipped.");
            System.out.println("Reason: missing DOI program argument.");
            System.out.println("Usage examples:");
            System.out.println("  ScopusCitationProbe 10.1016/j.ins.2016.01.021");
            System.out.println("  ScopusCitationProbe 10.1000/test");
            System.out.println("  ScopusCitationProbe --no-doi");
            System.out.println("  ScopusCitationProbe --blank-doi");
            return;
        }

        String doi = resolveDoiArgument(argument);

        ScopusApiSettings settings = ScopusApiSettings.fromEnvironment();

        System.out.println("=== SCOPUS CITATION PROBE ===");
        System.out.println("Scopus enabled: " + settings.isEnabled());
        System.out.println("Base URL: " + settings.baseUrl());
        System.out.println("Timeout seconds: " + settings.timeoutSeconds());
        System.out.println("Institutional token configured: " + settings.hasInstToken());
        System.out.println("Input argument: " + argument);
        System.out.println("Publication DOI value: " + printableDoi(doi));
        System.out.println("=============================");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .build();

        RealScopusConnector connector = new RealScopusConnector(httpClient, settings);

        Publication publication = new Publication(
                "Scopus citation probe publication",
                List.of(),
                null,
                null,
                doi,
                null,
                List.of(),
                null,
                SourceType.IRIS,
                RecordStatus.PARTIAL_DATA,
                null
        );

        Optional<CitationSummary> result = connector.fetchCitationSummary(publication);

        if (result.isEmpty()) {
            System.out.println("Scopus citation probe completed.");
            System.out.println("Result: citation summary not available.");
            return;
        }

        CitationSummary summary = result.get();

        System.out.println("Scopus citation probe completed.");
        System.out.println("Scopus citation count: " + summary.scopusCitationCount());
        System.out.println("Scholar citation count: " + summary.scholarCitationCount());
        System.out.println("Total citation count: " + summary.totalCitationCount());
    }

    private static String resolveDoiArgument(String argument) {
        if (NO_DOI_ARGUMENT.equalsIgnoreCase(argument)) {
            return null;
        }

        if (BLANK_DOI_ARGUMENT.equalsIgnoreCase(argument)) {
            return "   ";
        }

        return argument;
    }

    private static String printableDoi(String doi) {
        if (doi == null) {
            return "null";
        }

        if (doi.isBlank()) {
            return "\"\"";
        }

        return doi;
    }
}