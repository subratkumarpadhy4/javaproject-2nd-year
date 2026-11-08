package com.verivote.web;

import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.ledger.FileLedgerService;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.Candidate;
import com.verivote.data.entity.Election;
import com.verivote.data.repo.CandidateRepo;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.OperatorRepo;
import com.verivote.service.checkpoint.sink.FileSink;
import com.verivote.service.recovery.OperatorService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

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
    public ApplicationRunner initDatabase(
            ElectionRepo electionRepo,
            CandidateRepo candidateRepo,
            OperatorService operatorService,
            OperatorRepo operatorRepo
    ) {
        return args -> {
            // Seed Election 1
            Election election = electionRepo.findById(1L).orElseGet(() -> new Election("General Election"));
            if (election.getStatus() != Election.Status.OPEN) {
                election.setStatus(Election.Status.OPEN);
            }
            electionRepo.saveAndFlush(election);

            // Seed Candidates if empty
            if (candidateRepo.count() == 0) {
                candidateRepo.saveAndFlush(new Candidate(1L, "Alice Johnson", "Progressive Coalition"));
                candidateRepo.saveAndFlush(new Candidate(1L, "Bob Smith", "Reform Union"));
                candidateRepo.saveAndFlush(new Candidate(1L, "Charlie Davis", "Independent Alliance"));
            }

            // Seed Initial Operators if empty
            if (operatorRepo.count() == 0) {
                try {
                    KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
                    KeyPair kp1 = kpg.generateKeyPair();
                    KeyPair kp2 = kpg.generateKeyPair();
                    KeyPair kp3 = kpg.generateKeyPair();

                    operatorService.enroll("auditor-alpha", Base64Codec.encode(kp1.getPublic().getEncoded()), "AUDIT", "system");
                    operatorService.enroll("auditor-beta", Base64Codec.encode(kp2.getPublic().getEncoded()), "AUDIT", "system");
                    operatorService.enroll("trustee-charlie", Base64Codec.encode(kp3.getPublic().getEncoded()), "TALLY", "system");
                } catch (Exception e) {
                    System.err.println("Notice: Could not pre-seed operators: " + e.getMessage());
                }
            }
        };
    }
}
