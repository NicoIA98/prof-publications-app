package com.iadanza.profpublicationsapp.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iadanza.profpublicationsapp.domain.model.CitationSummary;
import com.iadanza.profpublicationsapp.domain.model.CitingDocument;
import com.iadanza.profpublicationsapp.domain.model.Publication;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Implementazione SQLite della cache citazionale.
 *
 * Per semplicità, il riepilogo citazionale e la lista dei documenti citanti
 * vengono salvati come JSON associati alla chiave della pubblicazione.
 */
public class SqliteCitationCacheRepository implements CitationCacheRepository {

    private final String jdbcUrl;
    private final ObjectMapper objectMapper;

    public SqliteCitationCacheRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        this.objectMapper = new ObjectMapper();
        initializeDatabase();
    }

    @Override
    public Optional<CitationSummary> findCitationSummary(Publication publication) {
        String sql = """
                SELECT citation_summary_json
                FROM publication_citation_cache
                WHERE publication_key = ?
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, buildPublicationKey(publication));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                String json = resultSet.getString("citation_summary_json");
                if (json == null || json.isBlank()) {
                    return Optional.empty();
                }

                CitationSummary summary = objectMapper.readValue(json, CitationSummary.class);
                return Optional.of(summary);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante la lettura del riepilogo citazionale da SQLite", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Errore durante la deserializzazione del riepilogo citazionale", e);
        }
    }

    @Override
    public List<CitingDocument> findCitingDocuments(Publication publication) {
        String sql = """
                SELECT citing_documents_json
                FROM publication_citation_cache
                WHERE publication_key = ?
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, buildPublicationKey(publication));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return List.of();
                }

                String json = resultSet.getString("citing_documents_json");
                if (json == null || json.isBlank()) {
                    return List.of();
                }

                return objectMapper.readValue(json, new TypeReference<>() {});
            }

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante la lettura dei documenti citanti da SQLite", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Errore durante la deserializzazione dei documenti citanti", e);
        }
    }

    @Override
    public void saveCitationData(Publication publication, CitationSummary summary, List<CitingDocument> citingDocuments) {
        String sql = """
                INSERT INTO publication_citation_cache (
                    publication_key,
                    publication_title,
                    doi,
                    citation_summary_json,
                    citing_documents_json,
                    last_refresh_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(publication_key) DO UPDATE SET
                    publication_title = excluded.publication_title,
                    doi = excluded.doi,
                    citation_summary_json = excluded.citation_summary_json,
                    citing_documents_json = excluded.citing_documents_json,
                    last_refresh_at = excluded.last_refresh_at
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            String summaryJson = summary != null ? objectMapper.writeValueAsString(summary) : null;
            String documentsJson = objectMapper.writeValueAsString(citingDocuments != null ? citingDocuments : List.of());

            statement.setString(1, buildPublicationKey(publication));
            statement.setString(2, publication.title());
            statement.setString(3, publication.doi());
            statement.setString(4, summaryJson);
            statement.setString(5, documentsJson);
            statement.setString(6, LocalDateTime.now().toString());

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio dei dati citazionali su SQLite", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Errore durante la serializzazione dei dati citazionali", e);
        }
    }

    @Override
    public Optional<String> findLastRefreshAt(Publication publication) {
        String sql = """
                SELECT last_refresh_at
                FROM publication_citation_cache
                WHERE publication_key = ?
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, buildPublicationKey(publication));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.ofNullable(resultSet.getString("last_refresh_at"));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante la lettura del timestamp citazionale da SQLite", e);
        }
    }

    private void initializeDatabase() {
        String sql = """
                CREATE TABLE IF NOT EXISTS publication_citation_cache (
                    publication_key TEXT PRIMARY KEY,
                    publication_title TEXT NOT NULL,
                    doi TEXT,
                    citation_summary_json TEXT,
                    citing_documents_json TEXT,
                    last_refresh_at TEXT NOT NULL
                )
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.execute();

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante l'inizializzazione della cache citazionale SQLite", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private String buildPublicationKey(Publication publication) {
        if (publication.doi() != null && !publication.doi().isBlank()) {
            return "doi:" + publication.doi().toLowerCase().trim();
        }

        String title = publication.title() != null ? publication.title().toLowerCase().trim() : "";
        int year = publication.year() != null ? publication.year() : 0;

        return "meta:" + title + "|" + year;
    }
}