package com.iadanza.profpublicationsapp.infrastructure.lookup;

import com.iadanza.profpublicationsapp.domain.model.ProfessorLookupEntry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface ProfessorLookupRepository {

    List<ProfessorLookupEntry> findAll();

    void add(ProfessorLookupEntry entry) throws IOException;

    void update(ProfessorLookupEntry oldEntry, ProfessorLookupEntry newEntry) throws IOException;

    Path getStoragePath();
}