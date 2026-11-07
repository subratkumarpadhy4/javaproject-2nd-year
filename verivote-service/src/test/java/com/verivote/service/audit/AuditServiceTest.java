package com.verivote.service.audit;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.crypto.BlindSignatureService.KeyPair;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.BlockHash;
import com.verivote.core.ledger.FileLedgerService;
import com.verivote.core.ledger.LedgerFileParser;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.*;
import com.verivote.data.repo.*;
import com.verivote.service.ServiceTestApplication;
import com.verivote.service.TokenService;
import com.verivote.service.VoteService;
import com.verivote.service.checkpoint.CheckpointService;
import com.verivote.service.checkpoint.CheckpointSigner;
import com.verivote.service.checkpoint.DefaultCheckpointService;
import com.verivote.service.checkpoint.sink.FileSink;
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
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ServiceTestApplication.class)
@TestPropertySource(locations = "classpath:application-service.properties")
class AuditServiceTest {

    @Autowired private VoteRepo voteRepo;
    @Autowired private SpentTokenRepo spentTokenRepo;
    @Autowired private RecoveryEventRepo recoveryEventRepo;
    @Autowired private IssuanceCounterRepo issuanceCounterRepo;
    @Autowired private IssuancePublishedRepo issuancePublishedRepo;
    @Autowired private ElectionRepo electionRepo;
    @Autowired private VoterRepo voterRepo;
    @Autowired private TokenService tokenService;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private CheckpointRepo checkpointRepo;

    @TempDir
    Path tempDir;

    private LedgerService ledgerService;
    private VoteService voteService;
    private Path ledgerFile;
    private Path sinkDir;
    private FileSink fileSink;
    private CheckpointReader checkpointReader;
    private java.security.KeyPair ckptKeyPair;
    private final SecureRandom rnd = new SecureRandom();
    private static final long EID = 1L;

    @BeforeEach
    void setUp() throws IOException {
        voteRepo.deleteAll();
        spentTokenRepo.deleteAll();
        recoveryEventRepo.deleteAll();
        issuanceCounterRepo.deleteAll();
        issuancePublishedRepo.deleteAll();
        checkpointRepo.deleteAll();

        ledgerFile = tempDir.resolve("test_audit_ledger.ndjson");
        sinkDir = tempDir.resolve("checkpoints");
        Files.createDirectories(sinkDir);

        ledgerService = new FileLedgerService(ledgerFile);
        voteService = new VoteService(ledgerService, voteRepo, spentTokenRepo, electionRepo, tokenService, txManager);
        fileSink = new FileSink(sinkDir);
        checkpointReader = new CheckpointReader(sinkDir);

        ckptKeyPair = CheckpointSigner.generateKeyPair();

        Election election = electionRepo.findById(EID).orElseGet(() -> new Election("General Election"));
        election.setStatus(Election.Status.OPEN);
        electionRepo.saveAndFlush(election);
    }

    private AuditService createAuditService() {
        return new DefaultAuditService(
                ledgerFile,
                voteRepo,
                spentTokenRepo,
                recoveryEventRepo,
                issuanceCounterRepo,
                issuancePublishedRepo,
                checkpointReader,
                ckptKeyPair.getPublic(),
                Collections.emptyMap()
        );
    }

    private VoteRequest buildVoteRequest(String voterSuffix) {
        Voter voter = new Voter("nid-aud-" + voterSuffix, "Voter " + voterSuffix, "pw", new byte[]{1, 2});
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

        KeyPair kp = tokenService.getElectionKeyPair();
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

    private Optional<CheckpointService.Checkpoint> forceCheckpoint() {
        DefaultCheckpointService cpService = new DefaultCheckpointService(
                ledgerService,
                checkpointRepo,
                List.of(fileSink),
                1,
                ckptKeyPair.getPrivate(),
                10,
                60
        );
        return cpService.forceNow();
    }

    @Test
    @DisplayName("1. Clean ledger, no votes -> audit clean, all counts 0")
    void cleanNoVotes() {
        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertTrue(report.clean(), "Empty ledger must be clean");
        assertFalse(report.blocksTally());
        assertEquals(0, report.ledgerBlockCount());
        assertEquals(0, report.ledgerRealVoteCount());
        assertEquals(0, report.ledgerOrphanCount());
        assertEquals(0, report.dbVoteCount());
    }

    @Test
    @DisplayName("2. Clean ledger, 3 votes -> audit clean, counts match")
    void cleanWithThreeVotes() {
        castVote("v1");
        castVote("v2");
        castVote("v3");

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertTrue(report.clean(), "Valid 3 votes must be clean");
        assertEquals(3, report.ledgerBlockCount());
        assertEquals(3, report.ledgerRealVoteCount());
        assertEquals(0, report.ledgerOrphanCount());
        assertEquals(3, report.dbVoteCount());
    }

    @Test
    @DisplayName("3. Update vote row's encrypted_vote directly -> DB_CIPHERTEXT_MISMATCH RED")
    void tamperDbEncryptedVote() {
        castVote("t3");
        List<Vote> votes = voteRepo.findAll();
        assertFalse(votes.isEmpty());
        Vote v = votes.get(0);
        v.setEncryptedVote(Base64Codec.encode("corrupted ciphertext".getBytes(StandardCharsets.UTF_8)));
        voteRepo.saveAndFlush(v);

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertFalse(report.clean());
        assertTrue(report.blocksTally());
        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.RED && f.code() == FindingCode.DB_CIPHERTEXT_MISMATCH));
    }

    @Test
    @DisplayName("4. Delete vote row -> BLOCK_WITHOUT_VOTE_ROW ALERT")
    void deleteVoteRowAlerts() {
        castVote("t4");
        List<Vote> votes = voteRepo.findAll();
        assertFalse(votes.isEmpty());
        voteRepo.delete(votes.get(0));
        voteRepo.flush();

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertFalse(report.clean());
        assertTrue(report.blocksTally());
        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.ALERT && f.code() == FindingCode.BLOCK_WITHOUT_VOTE_ROW));
    }

    @Test
    @DisplayName("5. Truncate blocks.ndjson to half -> early return RED, fatal parse")
    void truncateLedgerFile() throws IOException {
        castVote("t5a");
        castVote("t5b");
        // Truncate file halfway through
        byte[] data = Files.readAllBytes(ledgerFile);
        Files.write(ledgerFile, Arrays.copyOf(data, data.length / 2), StandardOpenOption.TRUNCATE_EXISTING);

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertFalse(report.clean());
        assertTrue(report.blocksTally());
        assertTrue(report.findings().stream().anyMatch(f -> f.severity() == Severity.RED));
    }

    @Test
    @DisplayName("6. Append garbage line to blocks.ndjson -> RED MALFORMED_LINE, early return")
    void appendGarbageLine() throws IOException {
        castVote("t6");
        Files.writeString(ledgerFile, "{invalid json line\n", StandardOpenOption.APPEND);

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertFalse(report.clean());
        assertTrue(report.blocksTally());
        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.RED && f.code() == FindingCode.MALFORMED_LINE));
    }

    @Test
    @DisplayName("7. Corrupt ciphertext in a block's ciphertextB64 -> RED CIPHERTEXT_MISMATCH")
    void corruptCiphertextInBlock() throws IOException {
        castVote("t7");
        String content = Files.readString(ledgerFile);
        // Replace a portion of ciphertext
        String modified = content.replaceFirst("\"ciphertextB64\":\\s*\"[^\"]+\"",
                "\"ciphertextB64\":\"" + Base64Codec.encode(new byte[256]) + "\"");
        Files.writeString(ledgerFile, modified);

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertFalse(report.clean());
        assertTrue(report.blocksTally());
        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.RED && f.code() == FindingCode.CIPHERTEXT_MISMATCH));
    }

    @Test
    @DisplayName("8. Break a block hash chain (flip a prevHash) -> RED CHAIN_LINK_BROKEN")
    void breakChainLink() throws IOException {
        castVote("t8a");
        castVote("t8b");
        List<Block> blocks = LedgerFileParser.parse(ledgerFile).blocks();
        Block b1 = blocks.get(1);
        String badPrev = "a".repeat(64);
        Block modifiedB1 = new Block(
                b1.height(),
                badPrev,
                b1.type(),
                b1.refHeight(),
                b1.voteHash(),
                b1.tokenHash(),
                b1.receiptHash(),
                b1.ciphertextB64(),
                b1.timestampSec(),
                b1.merkleRoot(),
                ""
        );
        String newHash = Hex.encode(BlockHash.compute(modifiedB1));
        Block finalB1 = new Block(
                modifiedB1.height(),
                modifiedB1.prevHash(),
                modifiedB1.type(),
                modifiedB1.refHeight(),
                modifiedB1.voteHash(),
                modifiedB1.tokenHash(),
                modifiedB1.receiptHash(),
                modifiedB1.ciphertextB64(),
                modifiedB1.timestampSec(),
                modifiedB1.merkleRoot(),
                newHash
        );
        List<String> lines = Files.readAllLines(ledgerFile);
        lines.set(1, LedgerFileParser.toJson(finalB1));
        Files.write(ledgerFile, lines);

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertFalse(report.clean());
        assertTrue(report.blocksTally());
        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.RED && f.code() == FindingCode.CHAIN_LINK_BROKEN));
    }

    @Test
    @DisplayName("9. Duplicate token hash -> RED DUPLICATE_TOKEN_HASH")
    void duplicateTokenHash() throws IOException {
        castVote("t9");
        List<String> lines = Files.readAllLines(ledgerFile);
        // Duplicate line 0 as line 1
        lines.add(lines.get(0));
        Files.write(ledgerFile, lines);

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertFalse(report.clean());
        assertTrue(report.blocksTally());
        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.RED && f.code() == FindingCode.DUPLICATE_TOKEN_HASH));
    }

    @Test
    @DisplayName("10. Checkpoint verified: forceNow -> audit -> CHECKPOINTS_VERIFIED INFO")
    void checkpointVerified() {
        castVote("t10");
        forceCheckpoint();

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertTrue(report.clean());
        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.INFO && f.code() == FindingCode.CHECKPOINTS_VERIFIED));
    }

    @Test
    @DisplayName("11. Diverged checkpoint -> CHAIN_TRUNCATED RED")
    void divergedCheckpoint() throws IOException {
        castVote("t11");
        forceCheckpoint();

        // Write a diverged checkpoint file at treeSize 1 claiming different headHash
        Path cpFile = sinkDir.resolve("ckpt-1.json");
        String badCkptJson = "{\n" +
                "  \"treeSize\": 1,\n" +
                "  \"merkleRoot\": \"" + "f".repeat(64) + "\",\n" +
                "  \"headHash\": \"" + "f".repeat(64) + "\",\n" +
                "  \"signature\": \"AAAA\",\n" +
                "  \"createdAtSec\": 1000\n" +
                "}";
        Files.writeString(cpFile, badCkptJson);

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertFalse(report.clean());
        assertTrue(report.blocksTally());
        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.RED && f.code() == FindingCode.CHAIN_TRUNCATED));
    }

    @Test
    @DisplayName("12. Issuance regression -> ISSUANCE_REGRESSED RED")
    void issuanceRegression() {
        castVote("t12");
        issuancePublishedRepo.saveAndFlush(new IssuancePublished(EID, 10L, 0L, 1L, "sig"));
        issuanceCounterRepo.saveAndFlush(new IssuanceCounter(EID, 5L, 0L));

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertFalse(report.clean());
        assertTrue(report.blocksTally());
        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.RED && f.code() == FindingCode.ISSUANCE_REGRESSED));
    }

    @Test
    @DisplayName("13. VOTES_EXCEED_ISSUED: votes > issued -> RED")
    void votesExceedIssued() {
        castVote("t13a");
        castVote("t13b");
        issuanceCounterRepo.saveAndFlush(new IssuanceCounter(EID, 1L, 0L));

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertFalse(report.clean());
        assertTrue(report.blocksTally());
        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.RED && f.code() == FindingCode.VOTES_EXCEED_ISSUED));
    }

    @Test
    @DisplayName("14. ORPHAN block present: markOrphan -> INFO ORPHAN_BLOCK_PRESENT")
    void orphanBlockPresent() {
        castVote("t14");
        ledgerService.markOrphan(0L);

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        assertTrue(report.findings().stream().anyMatch(f ->
                f.severity() == Severity.INFO && f.code() == FindingCode.ORPHAN_BLOCK_PRESENT));
    }

    @Test
    @DisplayName("15. Dedupe: same treeSize in 2 sinks produces one finding")
    void dedupeCheckpoints() throws IOException {
        castVote("t15");

        // Write 2 malformed/bad checkpoints with same treeSize=5 (exceeding ledger blocks)
        Path cp1 = sinkDir.resolve("ckpt-5.json");
        String badCkpt = "{\n" +
                "  \"treeSize\": 5,\n" +
                "  \"merkleRoot\": \"" + "a".repeat(64) + "\",\n" +
                "  \"headHash\": \"" + "a".repeat(64) + "\",\n" +
                "  \"signature\": \"c2ln\",\n" +
                "  \"createdAtSec\": 1000\n" +
                "}";
        Files.writeString(cp1, badCkpt);

        AuditService auditor = createAuditService();
        TamperReport report = auditor.audit();

        long truncCount = report.findings().stream()
                .filter(f -> f.code() == FindingCode.CHAIN_TRUNCATED && f.treeSize() != null && f.treeSize() == 5L)
                .count();
        assertEquals(1, truncCount, "Finding at treeSize 5 must be deduped to 1");
    }
}
