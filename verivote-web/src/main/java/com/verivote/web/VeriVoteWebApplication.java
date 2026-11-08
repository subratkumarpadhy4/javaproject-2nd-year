package com.verivote.web;

import com.verivote.core.ledger.FileLedgerService;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.Election;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.service.checkpoint.sink.FileSink;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication(scanBasePackages = {"com.verivote"})
@EntityScan(basePackages = {"com.verivote.data.entity"})
@EnableJpaRepositories(basePackages = {"com.verivote.data.repo"})
@EnableScheduling
public class VeriVoteWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeriVoteWebApplication.class, args);
    }

    @Bean
    public LedgerService ledgerService() {
        String pathStr = System.getenv("VERIVOTE_LEDGER_PATH");
        if (pathStr == null || pathStr.isBlank()) {
            pathStr = System.getProperty("verivote.ledger.path", "./ledger/blocks.ndjson");
        }
        return new FileLedgerService(Paths.get(pathStr));
    }

    @Bean
    public FileSink fileSink() {
        String dirStr = System.getenv("VERIVOTE_CHECKPOINT_DIR");
        if (dirStr == null || dirStr.isBlank()) {
            dirStr = System.getProperty("verivote.checkpoint.dir", "./ledger/checkpoints");
        }
        return new FileSink(dirStr);
    }

    @Bean
    public ApplicationRunner initDatabase(ElectionRepo electionRepo) {
        return args -> {
            Election election = electionRepo.findById(1L).orElseGet(() -> new Election("General Election"));
            if (election.getStatus() != Election.Status.OPEN) {
                election.setStatus(Election.Status.OPEN);
            }
            electionRepo.saveAndFlush(election);
        };
    }
}
