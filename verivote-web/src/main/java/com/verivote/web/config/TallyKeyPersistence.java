package com.verivote.web.config;

import com.verivote.service.tally.TallyKeyService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

/**
 * Keeps the election tally key stable across restarts when
 * {@code verivote.tally.key-file} is set, so ballots already stored in a
 * persistent database (file H2 / PostgreSQL) remain decryptable.
 * Leave the property blank (as the test profile does) for an ephemeral key.
 */
@Component
public class TallyKeyPersistence {

    private final TallyKeyService tallyKeyService;
    private final String keyFile;

    public TallyKeyPersistence(
            TallyKeyService tallyKeyService,
            @Value("${verivote.tally.key-file:}") String keyFile
    ) {
        this.tallyKeyService = tallyKeyService;
        this.keyFile = keyFile;
    }

    @PostConstruct
    void init() {
        if (keyFile != null && !keyFile.isBlank()) {
            tallyKeyService.loadOrPersist(Paths.get(keyFile.trim()));
        }
    }
}
