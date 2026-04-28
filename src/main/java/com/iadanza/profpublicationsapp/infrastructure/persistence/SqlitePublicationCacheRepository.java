package com.iadanza.profpublicationsapp.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iadanza.profpublicationsapp.domain.model.Professor;
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
 * Implementazione SQLite della cache delle pubblicazioni.
 *
 * Per semplicità, l'intera lista delle pubblicazioni del professore
 * viene salvata come JSON in una singola riga.
 */
public class SqlitePublicationCacheRepository implements PublicationCacheRepository {

    private final String jdbcUrl;
    private final ObjectMapper objectMapper;

    public SqlitePublicationCacheRepository(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        this.objectMapper = new ObjectMapper();
        initializeDatabase();
    }

    @Override
    public List<Publication> findCachedPublications(Professor professor) {
        String sql = """
                SELECT publications_json
                FROM professor_publication_cache
                WHERE professor_key = ?
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, buildProfessorKey(professor));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return List.of();
                }

                String publicationsJson = resultSet.getString("publications_json");
                return objectMapper.readValue(
                        publicationsJson,
                        new TypeReference<>() {}
                );
            }

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante la lettura della cache SQLite delle pubblicazioni", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Errore durante la deserializzazione JSON delle pubblicazioni", e);
        }
    }

    @Override
    public void savePublications(Professor professor, List<Publication> publications) {
        String sql = """
                INSERT INTO professor_publication_cache (
                    professor_key,
                    professor_full_name,
                    affiliation,
                    publications_json,
                    last_refresh_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(professor_key) DO UPDATE SET
                    professor_full_name = excluded.professor_full_name,
                    affiliation = excluded.affiliation,
                    publications_json = excluded.publications_json,
                    last_refresh_at = excluded.last_refresh_at
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            String publicationsJson = objectMapper.writeValueAsString(publications);

            statement.setString(1, buildProfessorKey(professor));
            statement.setString(2, professor.fullName());
            statement.setString(3, professor.affiliation());
            statement.setString(4, publicationsJson);
            statement.setString(5, LocalDateTime.now().toString());

            statement.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante il salvataggio della cache SQLite delle pubblicazioni", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Errore durante la serializzazione JSON delle pubblicazioni", e);
        }
    }

    @Override
    public Optional<String> findLastRefreshAt(Professor professor) {
        String sql = """
                SELECT last_refresh_at
                FROM professor_publication_cache
                WHERE professor_key = ?
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, buildProfessorKey(professor));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.ofNullable(resultSet.getString("last_refresh_at"));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante la lettura del timestamp di refresh", e);
        }
    }

    private void initializeDatabase() {
        String sql = """
                CREATE TABLE IF NOT EXISTS professor_publication_cache (
                    professor_key TEXT PRIMARY KEY,
                    professor_full_name TEXT NOT NULL,
                    affiliation TEXT,
                    publications_json TEXT NOT NULL,
                    last_refresh_at TEXT NOT NULL
                )
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.execute();

        } catch (SQLException e) {
            throw new RuntimeException("Errore durante l'inizializzazione del database SQLite", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private String buildProfessorKey(Professor professor) {
        return (professor.fullName() + "|" + professor.affiliation()).toLowerCase().trim();
    }
}