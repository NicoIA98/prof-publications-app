package com.iadanza.profpublicationsapp.infrastructure.config;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Repository per leggere/salvare le impostazioni locali dell'app.
 */
public interface LocalSettingsRepository {

    ConnectionSettings load() throws IOException;

    void save(ConnectionSettings settings) throws IOException;

    boolean exists();

    Path getSettingsPath();
}