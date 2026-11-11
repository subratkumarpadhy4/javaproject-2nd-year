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
import org.springframework.beans.factory.annotation.Value;
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
    public LedgerService ledgerService(@Value("${verivote.ledger.path:./ledger/blocks.ndjson}") String pathStr) {
        return new FileLedgerService(Paths.get(pathStr));
    }

    @Bean
    public FileSink fileSink(@Value("${verivote.checkpoint.dir:./ledger/checkpoints}") String dirStr) {
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
            // Seed Election 1: Independent Educational Demonstration
            Election election = electionRepo.findById(1L).orElseGet(() -> new Election("Indian General Election (Educational Demonstration)"));
            election.setTitle("Indian General Election (Educational Demonstration)");
            // Only transition from initial SETUP to OPEN so admin changes (e.g. CLOSED) survive restarts
            if (election.getStatus() == null || election.getStatus() == Election.Status.SETUP) {
                election.setStatus(Election.Status.OPEN);
            }
            electionRepo.saveAndFlush(election);

            // Seed Indian Political Parties if empty or outdated
            if (candidateRepo.count() == 0 || candidateRepo.findAll().stream().anyMatch(c -> "Alice Johnson".equals(c.getName()))) {
                candidateRepo.deleteAll();
                candidateRepo.saveAndFlush(new Candidate(1L, "Party Nominee (Configurable by Admin)", "Bharatiya Janata Party (BJP)", "Demo Constituency 01 (National)", "bjp"));
                candidateRepo.saveAndFlush(new Candidate(1L, "Party Nominee (Configurable by Admin)", "Telugu Desam Party (TDP)", "Demo Constituency 01 (National)", "tdp"));
                candidateRepo.saveAndFlush(new Candidate(1L, "Party Nominee (Configurable by Admin)", "Indian National Congress (INC)", "Demo Constituency 01 (National)", "inc"));
                candidateRepo.saveAndFlush(new Candidate(1L, "Party Nominee (Configurable by Admin)", "Yuvajana Sramika Rythu Congress Party (YSRCP)", "Demo Constituency 01 (National)", "ysrcp"));
                candidateRepo.saveAndFlush(new Candidate(1L, "Party Nominee (Configurable by Admin)", "Janata Dal (United) — JD(U)", "Demo Constituency 01 (National)", "jdu"));
                candidateRepo.saveAndFlush(new Candidate(1L, "Party Nominee (Configurable by Admin)", "Aam Aadmi Party (AAP)", "Demo Constituency 01 (National)", "aap"));
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
