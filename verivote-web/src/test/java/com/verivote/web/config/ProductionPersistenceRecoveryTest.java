package com.verivote.web.config;

import com.verivote.core.crypto.Sha256;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.FileLedgerService;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.Candidate;
import com.verivote.data.entity.Election;
import com.verivote.data.repo.CandidateRepo;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.OperatorRepo;
import com.verivote.service.recovery.OperatorService;
import com.verivote.web.VeriVoteWebApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProductionPersistenceRecoveryTest {

    @Autowired
    private ElectionRepo electionRepo;

    @Autowired
    private CandidateRepo candidateRepo;

    @Autowired
    private OperatorRepo operatorRepo;

    @Autowired
    private OperatorService operatorService;

    @Autowired
    private VeriVoteWebApplication application;

    @Test
    @DisplayName("Ledger state recovers fully across application restarts without data loss")
    void ledgerRecoversAcrossRestarts(@TempDir Path tempDir) {
        Path persistentLedgerPath = tempDir.resolve("persistent-blocks.ndjson");

        // 1. First run: Start ledger service and cast 3 test votes
        LedgerService run1 = new FileLedgerService(persistentLedgerPath);
        assertEquals(0, run1.blockCount(), "Initial height must be 0");

        byte[] ct1 = "cipher-ballot-1".getBytes(StandardCharsets.UTF_8);
        byte[] tok1 = Sha256.hash("token-1".getBytes(StandardCharsets.UTF_8));
        byte[] rc1 = Sha256.hash("receipt-1".getBytes(StandardCharsets.UTF_8));
        LedgerService.Appended app1 = run1.append(ct1, tok1, rc1);
        assertEquals(0, app1.height());

        byte[] ct2 = "cipher-ballot-2".getBytes(StandardCharsets.UTF_8);
        byte[] tok2 = Sha256.hash("token-2".getBytes(StandardCharsets.UTF_8));
        byte[] rc2 = Sha256.hash("receipt-2".getBytes(StandardCharsets.UTF_8));
        LedgerService.Appended app2 = run1.append(ct2, tok2, rc2);
        assertEquals(1, app2.height());

        byte[] ct3 = "cipher-ballot-3".getBytes(StandardCharsets.UTF_8);
        byte[] tok3 = Sha256.hash("token-3".getBytes(StandardCharsets.UTF_8));
        byte[] rc3 = Sha256.hash("receipt-3".getBytes(StandardCharsets.UTF_8));
        LedgerService.Appended app3 = run1.append(ct3, tok3, rc3);
        assertEquals(2, app3.height());

        assertEquals(3, run1.blockCount());
        Block b2Before = run1.at(2).orElseThrow();
        String finalRootBeforeRestart = b2Before.merkleRoot();
        String finalBlockHashBeforeRestart = b2Before.blockHash();

        // 2. Simulate Application Restart: Destroy run1 instance and instantiate a new FileLedgerService on same path
        LedgerService run2 = new FileLedgerService(persistentLedgerPath);

        // 3. Verify state recovered completely
        assertEquals(3, run2.blockCount(), "Height after restart must match exact block count");
        List<Block> recoveredBlocks = run2.range(0, 3);
        assertEquals(3, recoveredBlocks.size());

        Block b0 = recoveredBlocks.get(0);
        assertEquals(0, b0.height());
        assertEquals(Hex.encode(Sha256.hash(ct1)), b0.voteHash());

        Block b2 = recoveredBlocks.get(2);
        assertEquals(2, b2.height());
        assertEquals(finalRootBeforeRestart, b2.merkleRoot());
        assertEquals(finalBlockHashBeforeRestart, b2.blockHash());

        // 4. Verify subsequent append continues the chain seamlessly at height 3
        byte[] ct4 = "cipher-ballot-4".getBytes(StandardCharsets.UTF_8);
        byte[] tok4 = Sha256.hash("token-4".getBytes(StandardCharsets.UTF_8));
        byte[] rc4 = Sha256.hash("receipt-4".getBytes(StandardCharsets.UTF_8));
        LedgerService.Appended app4 = run2.append(ct4, tok4, rc4);

        assertEquals(3, app4.height());
        Block b3 = run2.at(3).orElseThrow();
        assertEquals(finalBlockHashBeforeRestart, b3.prevHash(), "New block must link to recovered block hash");
        assertEquals(4, run2.blockCount());
    }

    @Test
    @DisplayName("Admin election state (e.g. CLOSED) is preserved across application restart")
    void electionStatusPreservedAcrossRestart() throws Exception {
        // Find existing election seeded in initDatabase
        Election election = electionRepo.findById(1L).orElseThrow();
        // Simulate Admin closing the election
        election.setStatus(Election.Status.CLOSED);
        electionRepo.saveAndFlush(election);

        // Add a custom candidate configured by admin
        Candidate customCandidate = new Candidate(1L, "Custom Candidate", "Regional Independent", "Constituency 99", "ind");
        candidateRepo.saveAndFlush(customCandidate);
        long candidateCountBeforeRestart = candidateRepo.count();

        try {
            // Simulate application restart by triggering initDatabase runner again
            application.initDatabase(electionRepo, candidateRepo, operatorService, operatorRepo).run(null);

            // Verify status remained CLOSED and was not overridden to OPEN
            Election reloadedElection = electionRepo.findById(1L).orElseThrow();
            assertEquals(Election.Status.CLOSED, reloadedElection.getStatus(), "Admin CLOSED state must survive application restart");

            // Verify custom candidate was not wiped
            assertEquals(candidateCountBeforeRestart, candidateRepo.count(), "Custom admin candidates must survive application restart");
        } finally {
            // Restore status to OPEN so subsequent tests in the suite have an open election
            election.setStatus(Election.Status.OPEN);
            electionRepo.saveAndFlush(election);
            candidateRepo.delete(customCandidate);
        }
    }
}
