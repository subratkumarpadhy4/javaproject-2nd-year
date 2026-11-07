package com.verivote.data;

import com.verivote.data.entity.*;
import com.verivote.data.repo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
@TestPropertySource(locations = "classpath:application-data.properties")
class SchemaTest {

    @Autowired private VoterRepo voterRepo;
    @Autowired private SpentTokenRepo spentTokenRepo;
    @Autowired private VoteRepo voteRepo;
    @Autowired private CheckpointRepo checkpointRepo;
    @Autowired private OperatorRepo operatorRepo;
    @Autowired private RecoveryEventRepo recoveryEventRepo;
    @Autowired private IssuanceCounterRepo issuanceCounterRepo;
    @Autowired private IssuancePublishedRepo issuancePublishedRepo;
    @Autowired private IssuanceResetRepo issuanceResetRepo;
    @Autowired private ElectionRepo electionRepo;
    @Autowired private CandidateRepo candidateRepo;

    @Test
    @DisplayName("1. Context loads")
    void contextLoads() {
        assertNotNull(voterRepo);
    }

    @Test
    @DisplayName("2. Each entity saves and reads back")
    void saveAndReadBackAllEntities() {
        // Election
        Election election = new Election("General Election 2026");
        election.setStatus(Election.Status.OPEN);
        election.setOpensAt(Instant.now());
        Election savedElection = electionRepo.save(election);
        assertNotNull(savedElection.getId());
        assertTrue(electionRepo.findById(savedElection.getId()).isPresent());

        // Candidate
        Candidate candidate = new Candidate(savedElection.getId(), "Alice", "Integrity Party");
        Candidate savedCandidate = candidateRepo.save(candidate);
        assertNotNull(savedCandidate.getId());
        assertEquals("Alice", candidateRepo.findById(savedCandidate.getId()).orElseThrow().getName());

        // Voter
        Voter voter = new Voter("nid-hmac-1", "Voter One", "hash-pw-1", new byte[]{1, 2, 3});
        Voter savedVoter = voterRepo.save(voter);
        assertNotNull(savedVoter.getId());
        assertTrue(voterRepo.findByNationalIdHmac("nid-hmac-1").isPresent());

        // SpentToken
        SpentToken spentToken = new SpentToken("tok-hash-1", savedElection.getId());
        SpentToken savedSpent = spentTokenRepo.save(spentToken);
        assertEquals("tok-hash-1", savedSpent.getTokenHash());
        assertTrue(spentTokenRepo.findById("tok-hash-1").isPresent());

        // Vote
        Vote vote = new Vote(savedElection.getId(), "tok-hash-1", "rcpt-hash-1", "cipher-b64-1", 1L, "blk-hash-1");
        Vote savedVote = voteRepo.save(vote);
        assertNotNull(savedVote.getId());
        assertTrue(voteRepo.findByReceiptHash("rcpt-hash-1").isPresent());

        // Checkpoint
        Checkpoint cp = new Checkpoint(10, "merkle-root-1", "head-hash-1", "sig-cp-1");
        Checkpoint savedCp = checkpointRepo.save(cp);
        assertNotNull(savedCp.getId());

        // Operator
        Operator op = new Operator("admin-1", "ed-pub-1", Operator.Role.ADMIN, Instant.now(), "root");
        Operator savedOp = operatorRepo.save(op);
        assertNotNull(savedOp.getId());
        assertTrue(operatorRepo.findByName("admin-1").isPresent());

        // RecoveryEvent
        RecoveryEvent re = new RecoveryEvent(100L, RecoveryEvent.Action.ACCEPT_AS_ORPHAN,
                "blk-hash-100", "tok-100", "repair", "op-1", "sig-1", null, null);
        RecoveryEvent savedRe = recoveryEventRepo.save(re);
        assertNotNull(savedRe.getId());
        assertTrue(recoveryEventRepo.findByBlockHeight(100L).isPresent());

        // IssuanceCounter
        IssuanceCounter ic = new IssuanceCounter(savedElection.getId(), 50L, 2L);
        IssuanceCounter savedIc = issuanceCounterRepo.save(ic);
        assertEquals(50L, savedIc.getIssued());

        // IssuancePublished
        IssuancePublished ip = new IssuancePublished(savedElection.getId(), 50L, 2L, 48L, "sig-pub-1");
        IssuancePublished savedIp = issuancePublishedRepo.save(ip);
        assertNotNull(savedIp.getId());

        // IssuanceReset
        IssuanceReset ir = new IssuanceReset("voter-hmac-1", "admin-1", "lost token", "sig-rst-1");
        IssuanceReset savedIr = issuanceResetRepo.save(ir);
        assertNotNull(savedIr.getId());
    }

    @Test
    @DisplayName("3. Vote insert with null block_height or block_hash fails")
    void voteNullConstraint() {
        Vote badVote = new Vote(1L, "tok-bad", "rcpt-bad", "cipher", null, null);
        assertThrows(DataIntegrityViolationException.class, () -> voteRepo.saveAndFlush(badVote));
    }
}
