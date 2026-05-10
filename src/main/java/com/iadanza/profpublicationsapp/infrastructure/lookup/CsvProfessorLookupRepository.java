package com.iadanza.profpublicationsapp.infrastructure.lookup;

import com.iadanza.profpublicationsapp.domain.model.ProfessorLookupEntry;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Repository CSV modificabile per la rubrica Codici Fiscali.
 *
 * Il file iniziale resta in resources/lookup/professors-cf.csv.
 * Alla prima esecuzione viene copiato nella cartella utente:
 *
 * Windows:
 * C:\Users\<utente>\.prof-publications-app\professors-cf.csv
 *
 * In questo modo l'app può aggiungere/modificare righe anche quando verrà distribuita come jar.
 */
public class CsvProfessorLookupRepository implements ProfessorLookupRepository {

    private static final String HEADER = "nome;cognome;codiceFiscale";

    private final String initialResourcePath;
    private final Path storageDirectory;
    private final Path storagePath;

    public CsvProfessorLookupRepository(String initialResourcePath) {
        this.initialResourcePath = initialResourcePath;
        this.storageDirectory = Path.of(
                System.getProperty("user.home"),
                ".prof-publications-app"
        );
        this.storagePath = storageDirectory.resolve("professors-cf.csv");

        ensureLocalCsvExists();
    }

    @Override
    public List<ProfessorLookupEntry> findAll() {
        ensureLocalCsvExists();

        if (!Files.exists(storagePath)) {
            return List.of();
        }

        List<ProfessorLookupEntry> entries = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(storagePath, StandardCharsets.UTF_8)) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }

                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(";", -1);

                if (parts.length < 3) {
                    continue;
                }

                String nome = parts[0].trim();
                String cognome = parts[1].trim();
                String codiceFiscale = parts[2].trim();

                if (!nome.isBlank() && !cognome.isBlank() && !codiceFiscale.isBlank()) {
                    entries.add(new ProfessorLookupEntry(nome, cognome, codiceFiscale));
                }
            }
        } catch (IOException e) {
            return List.of();
        }

        return sort(entries);
    }

    @Override
    public void add(ProfessorLookupEntry entry) throws IOException {
        ensureLocalCsvExists();

        List<ProfessorLookupEntry> entries = new ArrayList<>(findAll());

        boolean alreadyExists = entries.stream()
                .anyMatch(existing -> existing.hasSameFiscalCode(entry));

        if (alreadyExists) {
            throw new IllegalArgumentException("Esiste già un docente con questo codice fiscale.");
        }

        entries.add(entry);
        writeAll(entries);
    }

    @Override
    public void update(ProfessorLookupEntry oldEntry, ProfessorLookupEntry newEntry) throws IOException {
        ensureLocalCsvExists();

        List<ProfessorLookupEntry> entries = new ArrayList<>(findAll());

        int indexToUpdate = -1;

        for (int i = 0; i < entries.size(); i++) {
            ProfessorLookupEntry current = entries.get(i);

            if (current.equals(oldEntry)) {
                indexToUpdate = i;
                break;
            }
        }

        if (indexToUpdate < 0) {
            throw new IllegalArgumentException("Docente non trovato nella rubrica locale.");
        }

        boolean duplicateFiscalCode = entries.stream()
                .filter(existing -> !existing.equals(oldEntry))
                .anyMatch(existing -> existing.hasSameFiscalCode(newEntry));

        if (duplicateFiscalCode) {
            throw new IllegalArgumentException("Esiste già un altro docente con questo codice fiscale.");
        }

        entries.set(indexToUpdate, newEntry);
        writeAll(entries);
    }

    @Override
    public Path getStoragePath() {
        return storagePath;
    }

    private void ensureLocalCsvExists() {
        try {
            Files.createDirectories(storageDirectory);

            if (Files.exists(storagePath)) {
                return;
            }

            try (InputStream inputStream = getClass().getResourceAsStream(initialResourcePath)) {
                if (inputStream == null) {
                    Files.writeString(storagePath, HEADER + System.lineSeparator(), StandardCharsets.UTF_8);
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                );
                     BufferedWriter writer = Files.newBufferedWriter(storagePath, StandardCharsets.UTF_8)
                ) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile inizializzare la rubrica locale CF.", e);
        }
    }

    private void writeAll(List<ProfessorLookupEntry> entries) throws IOException {
        Files.createDirectories(storageDirectory);

        List<ProfessorLookupEntry> sortedEntries = sort(entries);

        try (BufferedWriter writer = Files.newBufferedWriter(storagePath, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();

            for (ProfessorLookupEntry entry : sortedEntries) {
                writer.write(toCsvLine(entry));
                writer.newLine();
            }
        }
    }

    private String toCsvLine(ProfessorLookupEntry entry) {
        return escape(entry.nome())
                + ";"
                + escape(entry.cognome())
                + ";"
                + escape(entry.codiceFiscale());
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value.trim()
                .replace(";", " ")
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ");
    }

    private List<ProfessorLookupEntry> sort(List<ProfessorLookupEntry> entries) {
        return entries.stream()
                .sorted(
                        Comparator.comparing(
                                ProfessorLookupEntry::cognome,
                                String.CASE_INSENSITIVE_ORDER
                        ).thenComparing(
                                ProfessorLookupEntry::nome,
                                String.CASE_INSENSITIVE_ORDER
                        )
                )
                .toList();
    }
}