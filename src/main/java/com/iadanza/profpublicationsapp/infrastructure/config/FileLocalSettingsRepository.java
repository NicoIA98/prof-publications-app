package com.iadanza.profpublicationsapp.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Implementazione file-based del repository settings.
 *
 * Percorso:
 * user.home/.prof-publications-app/settings.properties
 *
 * Nota sicurezza:
 * - non stampa mai API key o password;
 * - crea la directory locale se non esiste;
 * - salva solo sul PC dell'utente.
 */
public class FileLocalSettingsRepository implements LocalSettingsRepository {

    private static final String APP_DIRECTORY_NAME = ".prof-publications-app";
    private static final String SETTINGS_FILE_NAME = "settings.properties";

    private static final String IRIS_REST_BASE_URL = "iris.rest.base-url";
    private static final String IRIS_REST_PATH_IR = "iris.rest.path-ir";
    private static final String IRIS_REST_PATH_RM = "iris.rest.path-rm";
    private static final String IRIS_REST_USERNAME = "iris.rest.username";
    private static final String IRIS_REST_PASSWORD = "iris.rest.password";
    private static final String IRIS_REST_TIMEOUT_SECONDS = "iris.rest.timeout-seconds";

    private static final String SCOPUS_BASE_URL = "scopus.base-url";
    private static final String SCOPUS_API_KEY = "scopus.api.key";
    private static final String SCOPUS_INST_TOKEN = "scopus.inst-token";
    private static final String SCOPUS_TIMEOUT_SECONDS = "scopus.timeout-seconds";

    private static final String SERPAPI_BASE_URL = "serpapi.base-url";
    private static final String SERPAPI_API_KEY = "serpapi.api-key";
    private static final String SERPAPI_TIMEOUT_SECONDS = "serpapi.timeout-seconds";

    private final Path settingsPath;

    public FileLocalSettingsRepository() {
        this.settingsPath = Path.of(
                System.getProperty("user.home"),
                APP_DIRECTORY_NAME,
                SETTINGS_FILE_NAME
        );
    }

    public FileLocalSettingsRepository(Path settingsPath) {
        this.settingsPath = settingsPath;
    }

    @Override
    public ConnectionSettings load() throws IOException {
        if (!Files.exists(settingsPath)) {
            return ConnectionSettings.empty();
        }

        Properties properties = new Properties();

        try (InputStream inputStream = Files.newInputStream(settingsPath)) {
            properties.load(inputStream);
        }

        ConnectionSettings settings = new ConnectionSettings(
                get(properties, IRIS_REST_BASE_URL, "https://iris.unicas.it:443/"),
                get(properties, IRIS_REST_PATH_IR, "rest/api/v1/"),
                get(properties, IRIS_REST_PATH_RM, "rm/restservices/api/v1"),
                get(properties, IRIS_REST_USERNAME, ""),
                get(properties, IRIS_REST_PASSWORD, ""),
                getInt(properties, IRIS_REST_TIMEOUT_SECONDS, 45),

                get(properties, SCOPUS_BASE_URL, "https://api.elsevier.com"),
                get(properties, SCOPUS_API_KEY, ""),
                get(properties, SCOPUS_INST_TOKEN, ""),
                getInt(properties, SCOPUS_TIMEOUT_SECONDS, 45),

                get(properties, SERPAPI_BASE_URL, "https://serpapi.com/search"),
                get(properties, SERPAPI_API_KEY, ""),
                getInt(properties, SERPAPI_TIMEOUT_SECONDS, 45)
        );

        return settings.normalized();
    }

    @Override
    public void save(ConnectionSettings settings) throws IOException {
        ConnectionSettings normalizedSettings = settings != null
                ? settings.normalized()
                : ConnectionSettings.empty();

        Files.createDirectories(settingsPath.getParent());

        Properties properties = new Properties();

        properties.setProperty(IRIS_REST_BASE_URL, normalizedSettings.irisRestBaseUrl());
        properties.setProperty(IRIS_REST_PATH_IR, normalizedSettings.irisRestPathIr());
        properties.setProperty(IRIS_REST_PATH_RM, normalizedSettings.irisRestPathRm());
        properties.setProperty(IRIS_REST_USERNAME, normalizedSettings.irisRestUsername());
        properties.setProperty(IRIS_REST_PASSWORD, normalizedSettings.irisRestPassword());
        properties.setProperty(IRIS_REST_TIMEOUT_SECONDS, String.valueOf(normalizedSettings.irisRestTimeoutSeconds()));

        properties.setProperty(SCOPUS_BASE_URL, normalizedSettings.scopusBaseUrl());
        properties.setProperty(SCOPUS_API_KEY, normalizedSettings.scopusApiKey());
        properties.setProperty(SCOPUS_INST_TOKEN, normalizedSettings.scopusInstToken());
        properties.setProperty(SCOPUS_TIMEOUT_SECONDS, String.valueOf(normalizedSettings.scopusTimeoutSeconds()));

        properties.setProperty(SERPAPI_BASE_URL, normalizedSettings.serpApiBaseUrl());
        properties.setProperty(SERPAPI_API_KEY, normalizedSettings.serpApiApiKey());
        properties.setProperty(SERPAPI_TIMEOUT_SECONDS, String.valueOf(normalizedSettings.serpApiTimeoutSeconds()));

        try (OutputStream outputStream = Files.newOutputStream(settingsPath)) {
            properties.store(
                    outputStream,
                    "Professor Publications App - local settings. Do not commit this file."
            );
        }
    }

    @Override
    public boolean exists() {
        return Files.exists(settingsPath);
    }

    @Override
    public Path getSettingsPath() {
        return settingsPath;
    }

    private String get(Properties properties, String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? value.trim() : defaultValue;
    }

    private int getInt(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);

        if (value == null || value.trim().isBlank()) {
            return defaultValue;
        }

        try {
            int parsedValue = Integer.parseInt(value.trim());
            return parsedValue > 0 ? parsedValue : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}