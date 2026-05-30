package com.iadanza.profpublicationsapp.infrastructure.lookup;

import com.iadanza.profpublicationsapp.domain.model.ProfessorLookupEntry;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Repository CSV modificabile per la Rubrica CF.
 *
 * Scelta privacy-friendly:
 * - il progetto NON distribuisce codici fiscali reali;
 * - al primo avvio viene creato un CSV locale vuoto;
 * - i dati inseriti dall'utente restano solo sul suo PC;
 * - il file locale è salvato in user.home/.prof-publications-app/professors-cf.csv.
 */
public class CsvProfessorLookupRepository implements ProfessorLookupRepository {

    private static final String HEADER = "nome;cognome;codiceFiscale";

    private final Path storageDirectory;
    private final Path storagePath;

    public CsvProfessorLookupRepository() {
        this.storageDirectory = Path.of(
                System.getProperty("user.home"),
                ".prof-publications-app"
        );
        this.storagePath = storageDirectory.resolve("professors-cf.csv");

        ensureLocalCsvExists();

        System.out.println("Rubrica CF locale: " + storagePath);
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
            System.out.println("Errore lettura Rubrica CF locale: " + e.getMessage());
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
    public void delete(ProfessorLookupEntry entry) throws IOException {
        ensureLocalCsvExists();

        List<ProfessorLookupEntry> entries = new ArrayList<>(findAll());

        boolean removed = entries.removeIf(existing -> existing.equals(entry));

        if (!removed) {
            throw new IllegalArgumentException("Docente non trovato nella rubrica locale.");
        }

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

            Files.writeString(
                    storagePath,
                    HEADER + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );

            System.out.println("Rubrica CF locale creata vuota: " + storagePath);
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