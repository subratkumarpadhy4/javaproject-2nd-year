package com.verivote.service.recovery;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.FileLedgerService;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.RecoveryData;
import com.verivote.data.entity.*;
import com.verivote.data.repo.*;
import com.verivote.service.ServiceTestApplication;
import com.verivote.service.TokenService;
import com.verivote.service.VoteService;
import com.verivote.service.audit.AuditService;
import com.verivote.service.audit.DefaultAuditService;
import com.verivote.service.audit.TamperReport;
import com.verivote.service.checkpoint.CheckpointSigner;
import com.verivote.service.dto.TokenIssuanceResponse;
import com.verivote.service.dto.VoteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ServiceTestApplication.class)
@TestPropertySource(locations = "classpath:application-service.properties")
class DefaultRecoveryServiceTest {

    @Autowired private VoteRepo voteRepo;
    @Autowired private SpentTokenRepo spentTokenRepo;
    @Autowired private RecoveryEventRepo recoveryEventRepo;
    @Autowired private IssuanceCounterRepo issuanceCounterRepo;
    @Autowired private IssuancePublishedRepo issuancePublishedRepo;
    @Autowired private ElectionRepo electionRepo;
    @Autowired private VoterRepo voterRepo;
    @Autowired private OperatorRepo operatorRepo;
    @Autowired private OperatorService operatorService;
    @Autowired private TokenService tokenService;
    @Autowired private RecoveryData recoveryData;
    @Autowired private PlatformTransactionManager txManager;

    @TempDir
    Path tempDir;

    private LedgerService ledgerService;
    private VoteService voteService;
    private DefaultRecoveryService recoveryService;
    private Path ledgerFile;
    private Path fallbackFile;
    private Path sidecarFile;

    private KeyPair kpAlpha;
    private KeyPair kpBeta;
    private final SecureRandom rnd = new SecureRandom();
    private static final long EID = 1L;

    @BeforeEach
    void setUp() throws IOException {
        voteRepo.deleteAll();
        voterRepo.deleteAll();
        spentTokenRepo.deleteAll();
        recoveryEventRepo.deleteAll();
        issuanceCounterRepo.deleteAll();
        issuancePublishedRepo.deleteAll();
        operatorRepo.deleteAll();

        ledgerFile = tempDir.resolve("test_recovery_ledger.ndjson");
        fallbackFile = tempDir.resolve("orphans.fallback.ndjson");
        sidecarFile = tempDir.resolve("recovery.ndjson");

        ledgerService = new FileLedgerService(ledgerFile);
        voteService = new VoteService(ledgerService, voteRepo, spentTokenRepo, electionRepo, tokenService, txManager);

        recoveryService = new DefaultRecoveryService(
                ledgerService,
                recoveryData,
                spentTokenRepo,
                voteRepo,
                operatorService,
                electionRepo,
                txManager,
                fallbackFile,
                sidecarFile
        );

        Election election = electionRepo.findById(EID).orElseGet(() -> new Election("General Election"));
        election.setStatus(Election.Status.OPEN);
        electionRepo.saveAndFlush(election);

        kpAlpha = CheckpointSigner.generateKeyPair();
        kpBeta = CheckpointSigner.generateKeyPair();

        operatorService.enroll("operator-alpha", CheckpointSigner.encodePublicKey(kpAlpha), "AUDIT", "admin");
        operatorService.enroll("operator-beta", CheckpointSigner.encodePublicKey(kpBeta), "AUDIT", "admin");

        // Cast 3 votes while election is OPEN
        castVote("rec1");
        castVote("rec2");
        castVote("rec3");

        // Close election for recovery operations
        election.setStatus(Election.Status.CLOSED);
        electionRepo.saveAndFlush(election);
    }

    private VoteRequest buildVoteRequest(String voterSuffix) {
        Voter voter = new Voter("nid-rec-" + voterSuffix, "Voter " + voterSuffix, "pw", new byte[]{1, 2});
        voter = voterRepo.saveAndFlush(voter);

        byte[] tokenPlain = new byte[32];
        rnd.nextBytes(tokenPlain);

        byte[] clientNonce = new byte[32];
        rnd.nextBytes(clientNonce);

        byte[] ciphertext = new byte[256];
        rnd.nextBytes(ciphertext);

        byte[] prefix = DomainStrings.TOKEN_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] eidBytes = BigEndian.u64be(EID);
        byte[] preimage = new byte[prefix.length + eidBytes.length + tokenPlain.length];
        System.arraycopy(prefix, 0, preimage, 0, prefix.length);
        System.arraycopy(eidBytes, 0, preimage, prefix.length, eidBytes.length);
        System.arraycopy(tokenPlain, 0, preimage, prefix.length + eidBytes.length, tokenPlain.length);

        BlindSignatureService.KeyPair kp = tokenService.getElectionKeyPair();
        BigInteger[] bf = new BigInteger[1];
        byte[] blinded = BlindSignatureService.blind(preimage, kp, rnd, bf);

        TokenIssuanceResponse issuance = tokenService.issueToken(voter.getId(), blinded);
        byte[] sigBytes = Base64Codec.decode(issuance.signature());
        byte[] unblinded = BlindSignatureService.unblind(sigBytes, bf[0].toByteArray(), kp, 256);

        return new VoteRequest(ciphertext, tokenPlain, unblinded, clientNonce);
    }

    private void castVote(String suffix) {
        VoteRequest req = buildVoteRequest(suffix);
        voteService.castVote(req);
    }

    private byte[] sign(long height, RecoveryRequest.Action action, Block b, String reason, KeyPair kp) {
        byte[] preimage = DefaultRecoveryService.buildSigningPreimage(
                height,
                action,
                b.blockHash(),
                b.tokenHash(),
                reason
        );
        return RecoveryOperatorSigner.sign(preimage, kp.getPrivate());
    }

    @Test
    @DisplayName("1. ACCEPT_AS_ORPHAN happy path: restores audit to clean")
    void acceptAsOrphanHappyPath() {
        Block b1 = ledgerService.at(1L).orElseThrow();
        // Simulate DB loss before commit: delete vote row and spent_token
        voteRepo.findByBlockHeight(1L).ifPresent(voteRepo::delete);
        spentTokenRepo.deleteById(b1.tokenHash());
        voteRepo.flush();
        spentTokenRepo.flush();

        DefaultAuditService audit = new DefaultAuditService(
                ledgerFile, voteRepo, spentTokenRepo, recoveryEventRepo,
                issuanceCounterRepo, issuancePublishedRepo, null, null,
                Map.of("operator-alpha", kpAlpha.getPublic(), "operator-beta", kpBeta.getPublic())
        );
        TamperReport reportBefore = audit.audit();
        // Since vote row is missing and no spent_token exists, audit reports BLOCK_WITHOUT_VOTE_ROW WARN
        assertNotNull(reportBefore);

        String reason = "Crash before commit simulation";
        byte[] sig1 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpAlpha);
        byte[] sig2 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpBeta);

        RecoveryRequest req = new RecoveryRequest(
                1L,
                RecoveryRequest.Action.ACCEPT_AS_ORPHAN,
                reason,
                b1.blockHash(),
                b1.tokenHash(),
                "operator-alpha",
                sig1,
                "operator-beta",
                sig2
        );

        RecoveryResult result = recoveryService.execute(req);
        assertNotNull(result);
        assertEquals(1L, result.blockHeight());
        assertEquals(RecoveryRequest.Action.ACCEPT_AS_ORPHAN, result.action());
        assertTrue(ledgerService.isOrphaned(1L));

        TamperReport reportAfter = audit.audit();
        assertTrue(reportAfter.clean(), "Audit must be clean after ACCEPT_AS_ORPHAN");
    }

    @Test
    @DisplayName("2. ACCEPT_AS_ORPHAN with same operator twice -> SAME_OPERATOR")
    void sameOperatorThrows() {
        Block b1 = ledgerService.at(1L).orElseThrow();
        String reason = "test";
        byte[] sig1 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpAlpha);

        RecoveryRequest req = new RecoveryRequest(
                1L,
                RecoveryRequest.Action.ACCEPT_AS_ORPHAN,
                reason,
                b1.blockHash(),
                b1.tokenHash(),
                "operator-alpha",
                sig1,
                "operator-alpha",
                sig1
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.SAME_OPERATOR, ex.getCode());
    }

    @Test
    @DisplayName("3. ACCEPT_AS_ORPHAN with inactive operator -> OPERATOR_INACTIVE")
    void inactiveOperatorThrows() {
        operatorService.deactivate("operator-beta");
        Block b1 = ledgerService.at(1L).orElseThrow();
        String reason = "test";
        byte[] sig1 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpAlpha);
        byte[] sig2 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpBeta);

        RecoveryRequest req = new RecoveryRequest(
                1L,
                RecoveryRequest.Action.ACCEPT_AS_ORPHAN,
                reason,
                b1.blockHash(),
                b1.tokenHash(),
                "operator-alpha",
                sig1,
                "operator-beta",
                sig2
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.OPERATOR_INACTIVE, ex.getCode());
    }

    @Test
    @DisplayName("4. ACCEPT_AS_ORPHAN with wrong role -> OPERATOR_ROLE_MISMATCH")
    void wrongRoleOperatorThrows() {
        KeyPair kpGamma = CheckpointSigner.generateKeyPair();
        operatorService.enroll("operator-gamma", CheckpointSigner.encodePublicKey(kpGamma), "ADMIN", "admin");

        Block b1 = ledgerService.at(1L).orElseThrow();
        String reason = "test";
        byte[] sig1 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpAlpha);
        byte[] sigGamma = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpGamma);

        RecoveryRequest req = new RecoveryRequest(
                1L,
                RecoveryRequest.Action.ACCEPT_AS_ORPHAN,
                reason,
                b1.blockHash(),
                b1.tokenHash(),
                "operator-alpha",
                sig1,
                "operator-gamma",
                sigGamma
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.OPERATOR_ROLE_MISMATCH, ex.getCode());
    }

    @Test
    @DisplayName("5. ACCEPT_AS_ORPHAN with unknown operator -> OPERATOR_UNKNOWN")
    void unknownOperatorThrows() {
        Block b1 = ledgerService.at(1L).orElseThrow();
        String reason = "test";
        byte[] sig1 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpAlpha);

        RecoveryRequest req = new RecoveryRequest(
                1L,
                RecoveryRequest.Action.ACCEPT_AS_ORPHAN,
                reason,
                b1.blockHash(),
                b1.tokenHash(),
                "operator-unknown",
                sig1,
                "operator-beta",
                sig1
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.OPERATOR_UNKNOWN, ex.getCode());
    }

    @Test
    @DisplayName("6. ACCEPT_AS_ORPHAN with invalid signature -> SIGNATURE_INVALID")
    void invalidSignatureThrows() {
        Block b1 = ledgerService.at(1L).orElseThrow();
        String reason = "test";
        byte[] sig1 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpAlpha);
        byte[] sig2 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpBeta);
        sig1[0] ^= 0x01; // tamper

        // Ensure precondition: delete spent_token so signature is reached
        spentTokenRepo.deleteById(b1.tokenHash());
        spentTokenRepo.flush();

        RecoveryRequest req = new RecoveryRequest(
                1L,
                RecoveryRequest.Action.ACCEPT_AS_ORPHAN,
                reason,
                b1.blockHash(),
                b1.tokenHash(),
                "operator-alpha",
                sig1,
                "operator-beta",
                sig2
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.SIGNATURE_INVALID, ex.getCode());
    }

    @Test
    @DisplayName("7. ACCEPT_AS_ORPHAN on already-orphaned block -> BLOCK_ALREADY_ORPHANED")
    void alreadyOrphanedThrows() {
        ledgerService.markOrphan(0L);
        Block b0 = ledgerService.at(0L).orElseThrow();
        String reason = "test";
        byte[] sig1 = sign(0L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b0, reason, kpAlpha);
        byte[] sig2 = sign(0L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b0, reason, kpBeta);

        RecoveryRequest req = new RecoveryRequest(
                0L,
                RecoveryRequest.Action.ACCEPT_AS_ORPHAN,
                reason,
                b0.blockHash(),
                b0.tokenHash(),
                "operator-alpha",
                sig1,
                "operator-beta",
                sig2
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.BLOCK_ALREADY_ORPHANED, ex.getCode());
    }

    @Test
    @DisplayName("8. RECOVER_FROM_LEDGER happy path: restores vote row and audit clean")
    void recoverFromLedgerHappyPath() {
        Block b2 = ledgerService.at(2L).orElseThrow();
        // Delete vote row directly, KEEP spent_token
        voteRepo.findByBlockHeight(2L).ifPresent(voteRepo::delete);
        voteRepo.flush();

        DefaultAuditService audit = new DefaultAuditService(
                ledgerFile, voteRepo, spentTokenRepo, recoveryEventRepo,
                issuanceCounterRepo, issuancePublishedRepo, null, null,
                Map.of("operator-alpha", kpAlpha.getPublic())
        );
        TamperReport reportBefore = audit.audit();
        assertFalse(reportBefore.clean());
        assertTrue(reportBefore.blocksTally());

        String reason = "Restore deleted vote row";
        byte[] sig1 = sign(2L, RecoveryRequest.Action.RECOVER_FROM_LEDGER, b2, reason, kpAlpha);

        RecoveryRequest req = new RecoveryRequest(
                2L,
                RecoveryRequest.Action.RECOVER_FROM_LEDGER,
                reason,
                b2.blockHash(),
                b2.tokenHash(),
                "operator-alpha",
                sig1,
                null,
                null
        );

        RecoveryResult res = recoveryService.execute(req);
        assertNotNull(res);
        assertEquals(2L, res.blockHeight());
        assertEquals(RecoveryRequest.Action.RECOVER_FROM_LEDGER, res.action());

        Optional<Vote> restored = voteRepo.findByBlockHeight(2L);
        assertTrue(restored.isPresent(), "Vote row must be restored in DB");

        TamperReport reportAfter = audit.audit();
        assertTrue(reportAfter.clean(), "Audit must be clean after RECOVER_FROM_LEDGER");
    }

    @Test
    @DisplayName("9. RECOVER_FROM_LEDGER without spent_token -> PRECONDITION_FAILED")
    void recoverWithoutSpentTokenThrows() {
        Block b2 = ledgerService.at(2L).orElseThrow();
        spentTokenRepo.deleteById(b2.tokenHash());
        spentTokenRepo.flush();

        String reason = "test";
        byte[] sig1 = sign(2L, RecoveryRequest.Action.RECOVER_FROM_LEDGER, b2, reason, kpAlpha);

        RecoveryRequest req = new RecoveryRequest(
                2L,
                RecoveryRequest.Action.RECOVER_FROM_LEDGER,
                reason,
                b2.blockHash(),
                b2.tokenHash(),
                "operator-alpha",
                sig1,
                null,
                null
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.PRECONDITION_FAILED, ex.getCode());
    }

    @Test
    @DisplayName("10. Election not CLOSED -> ELECTION_NOT_CLOSED")
    void electionNotClosedThrows() {
        Election e = electionRepo.findById(EID).orElseThrow();
        e.setStatus(Election.Status.OPEN);
        electionRepo.saveAndFlush(e);

        Block b1 = ledgerService.at(1L).orElseThrow();
        String reason = "test";
        byte[] sig1 = sign(1L, RecoveryRequest.Action.RECOVER_FROM_LEDGER, b1, reason, kpAlpha);

        RecoveryRequest req = new RecoveryRequest(
                1L,
                RecoveryRequest.Action.RECOVER_FROM_LEDGER,
                reason,
                b1.blockHash(),
                b1.tokenHash(),
                "operator-alpha",
                sig1,
                null,
                null
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.ELECTION_NOT_CLOSED, ex.getCode());
    }

    @Test
    @DisplayName("11. Block doesn't exist -> BLOCK_MISSING")
    void blockMissingThrows() {
        String reason = "test";
        RecoveryRequest req = new RecoveryRequest(
                999L,
                RecoveryRequest.Action.RECOVER_FROM_LEDGER,
                reason,
                "a".repeat(64),
                "b".repeat(64),
                "operator-alpha",
                new byte[64],
                null,
                null
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.BLOCK_MISSING, ex.getCode());
    }

    @Test
    @DisplayName("12. Block is ORPHAN -> BLOCK_NOT_VOTE")
    void blockIsOrphanThrows() {
        // Mark orphan creates an ORPHAN block at height 3
        ledgerService.markOrphan(0L);
        Block bOrphan = ledgerService.at(3L).orElseThrow();
        assertEquals(Block.ORPHAN, bOrphan.type());

        String reason = "test";
        RecoveryRequest req = new RecoveryRequest(
                3L,
                RecoveryRequest.Action.RECOVER_FROM_LEDGER,
                reason,
                bOrphan.blockHash(),
                bOrphan.tokenHash(),
                "operator-alpha",
                new byte[64],
                null,
                null
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.BLOCK_NOT_VOTE, ex.getCode());
    }

    @Test
    @DisplayName("13. Fix F1 crash-resume test: resumes markOrphan, second call throws ALREADY_RECOVERED")
    void fixF1CrashResume() {
        Block b1 = ledgerService.at(1L).orElseThrow();
        // Manually insert RecoveryEvent at height 1 (simulating crash after commit before markOrphan)
        RecoveryData.EventRow preEvent = new RecoveryData.EventRow(
                0L,
                1L,
                RecoveryRequest.Action.ACCEPT_AS_ORPHAN.name(),
                b1.blockHash(),
                b1.tokenHash(),
                "Pre-crash event",
                "operator-alpha",
                Base64Codec.encode(new byte[64]),
                "operator-beta",
                Base64Codec.encode(new byte[64])
        );
        long preId = recoveryData.insertEvent(preEvent);
        assertFalse(ledgerService.isOrphaned(1L));

        RecoveryRequest resumeReq = new RecoveryRequest(
                1L,
                RecoveryRequest.Action.ACCEPT_AS_ORPHAN,
                "Pre-crash event",
                b1.blockHash(),
                b1.tokenHash(),
                "operator-alpha",
                new byte[64],
                "operator-beta",
                new byte[64]
        );

        // First call resumes side-effect
        RecoveryResult res = recoveryService.execute(resumeReq);
        assertEquals(preId, res.eventId());
        assertTrue(ledgerService.isOrphaned(1L), "markOrphan must have been called on resume");

        // Second call throws ALREADY_RECOVERED
        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(resumeReq));
        assertEquals(RecoveryException.Code.ALREADY_RECOVERED, ex.getCode());
    }

    @Test
    @DisplayName("14. Fix F2 race test: pre-inserted event resumes or throws ALREADY_RECOVERED")
    void fixF2RaceTest() {
        Block b2 = ledgerService.at(2L).orElseThrow();
        RecoveryData.EventRow ev = new RecoveryData.EventRow(
                0L,
                2L,
                RecoveryRequest.Action.RECOVER_FROM_LEDGER.name(),
                b2.blockHash(),
                b2.tokenHash(),
                "Race test",
                "operator-alpha",
                Base64Codec.encode(new byte[64]),
                null,
                null
        );
        recoveryData.insertEvent(ev);

        RecoveryRequest req = new RecoveryRequest(
                2L,
                RecoveryRequest.Action.RECOVER_FROM_LEDGER,
                "Race test",
                b2.blockHash(),
                b2.tokenHash(),
                "operator-alpha",
                new byte[64],
                null,
                null
        );

        RecoveryException ex = assertThrows(RecoveryException.class, () -> recoveryService.execute(req));
        assertEquals(RecoveryException.Code.ALREADY_RECOVERED, ex.getCode());
    }

    @Test
    @DisplayName("15. markOrphan failure -> orphans.fallback.ndjson written")
    void markOrphanFailureWritesFallback() {
        LedgerService throwingLedger = new LedgerService() {
            @Override public Appended append(byte[] ciphertext, byte[] tokenHash, byte[] receiptHash) { return ledgerService.append(ciphertext, tokenHash, receiptHash); }
            @Override public void markOrphan(long height) { throw new RuntimeException("Simulated disk error during markOrphan"); }
            @Override public boolean isOrphaned(long height) { return ledgerService.isOrphaned(height); }
            @Override public long blockCount() { return ledgerService.blockCount(); }
            @Override public long realVoteCount() { return ledgerService.realVoteCount(); }
            @Override public long orphanCount() { return ledgerService.orphanCount(); }
            @Override public String headHash() { return ledgerService.headHash(); }
            @Override public Optional<Block> at(long height) { return ledgerService.at(height); }
            @Override public List<Block> range(long fromIncl, long toExcl) { return ledgerService.range(fromIncl, toExcl); }
            @Override public byte[] rootAt(long treeSize) { return ledgerService.rootAt(treeSize); }
            @Override public List<byte[]> proof(long index, long treeSize) { return ledgerService.proof(index, treeSize); }
        };

        DefaultRecoveryService failRecoveryService = new DefaultRecoveryService(
                throwingLedger,
                recoveryData,
                spentTokenRepo,
                voteRepo,
                operatorService,
                electionRepo,
                txManager,
                fallbackFile,
                sidecarFile
        );

        Block b1 = ledgerService.at(1L).orElseThrow();
        spentTokenRepo.deleteById(b1.tokenHash());
        spentTokenRepo.flush();

        String reason = "Fallback test";
        byte[] sig1 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpAlpha);
        byte[] sig2 = sign(1L, RecoveryRequest.Action.ACCEPT_AS_ORPHAN, b1, reason, kpBeta);

        RecoveryRequest req = new RecoveryRequest(
                1L,
                RecoveryRequest.Action.ACCEPT_AS_ORPHAN,
                reason,
                b1.blockHash(),
                b1.tokenHash(),
                "operator-alpha",
                sig1,
                "operator-beta",
                sig2
        );

        assertThrows(RuntimeException.class, () -> failRecoveryService.execute(req));
        assertTrue(Files.exists(fallbackFile), "orphans.fallback.ndjson must be written on markOrphan failure");
    }
}
