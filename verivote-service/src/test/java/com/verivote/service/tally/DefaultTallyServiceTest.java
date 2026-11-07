package com.verivote.service.tally;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.crypto.Shamir;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.FileLedgerService;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.*;
import com.verivote.data.repo.*;
import com.verivote.service.ServiceTestApplication;
import com.verivote.service.TokenService;
import com.verivote.service.VoteService;
import com.verivote.service.audit.AuditService;
import com.verivote.service.audit.CheckpointReader;
import com.verivote.service.audit.DefaultAuditService;
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
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ServiceTestApplication.class)
@TestPropertySource(locations = "classpath:application-service.properties")
class DefaultTallyServiceTest {

    @Autowired private VoteRepo voteRepo;
    @Autowired private SpentTokenRepo spentTokenRepo;
    @Autowired private RecoveryEventRepo recoveryEventRepo;
    @Autowired private IssuanceCounterRepo issuanceCounterRepo;
    @Autowired private IssuancePublishedRepo issuancePublishedRepo;
    @Autowired private ElectionRepo electionRepo;
    @Autowired private CandidateRepo candidateRepo;
    @Autowired private VoterRepo voterRepo;
    @Autowired private TokenService tokenService;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private CheckpointRepo checkpointRepo;
    @Autowired private TallyKeyService tallyKeyService;

    @TempDir
    Path tempDir;

    private LedgerService ledgerService;
    private VoteService voteService;
    private CheckpointService checkpointService;
    private AuditService auditService;
    private DefaultTallyService tallyService;

    private Path ledgerFile;
    private Path sinkDir;
    private KeyPair ckptKeyPair;
    private final SecureRandom rnd = new SecureRandom();
    private static final long EID = 1L;

    private Candidate candA;
    private Candidate candB;
    private Candidate candC;

    @BeforeEach
    void setUp() throws IOException {
        voterRepo.deleteAll();
        voteRepo.deleteAll();
        spentTokenRepo.deleteAll();
        recoveryEventRepo.deleteAll();
        issuanceCounterRepo.deleteAll();
        issuancePublishedRepo.deleteAll();
        checkpointRepo.deleteAll();
        candidateRepo.deleteAll();
        // Do not delete electionRepo to preserve auto-increment ID 1
        Election election = electionRepo.findById(EID).orElseGet(() -> new Election("2026 Presidential"));
        election.setStatus(Election.Status.OPEN);
        electionRepo.saveAndFlush(election);

        ledgerFile = tempDir.resolve("test_tally_ledger.ndjson");
        sinkDir = tempDir.resolve("checkpoints");
        Files.createDirectories(sinkDir);

        ledgerService = new FileLedgerService(ledgerFile);
        voteService = new VoteService(ledgerService, voteRepo, spentTokenRepo, electionRepo, tokenService, txManager);

        ckptKeyPair = CheckpointSigner.generateKeyPair();
        FileSink fileSink = new FileSink(sinkDir);
        checkpointService = new DefaultCheckpointService(
                ledgerService,
                checkpointRepo,
                List.of(fileSink),
                1,
                ckptKeyPair.getPrivate(),
                1,
                300
        );

        CheckpointReader checkpointReader = new CheckpointReader(sinkDir);
        auditService = new DefaultAuditService(
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

        tallyService = new DefaultTallyService(
                ledgerService,
                auditService,
                checkpointService,
                electionRepo,
                candidateRepo,
                tallyKeyService,
                txManager
        );

        candA = candidateRepo.saveAndFlush(new Candidate(EID, "Alice", "Party A"));
        candB = candidateRepo.saveAndFlush(new Candidate(EID, "Bob", "Party B"));
        candC = candidateRepo.saveAndFlush(new Candidate(EID, "Charlie", "Party C"));
    }

    private void castEncryptedVote(String voterSuffix, int candidateId) {
        Voter voter = new Voter("nid-tally-" + voterSuffix, "Voter " + voterSuffix, "pw", new byte[]{1, 2});
        voter = voterRepo.saveAndFlush(voter);

        byte[] tokenPlain = new byte[32];
        rnd.nextBytes(tokenPlain);

        byte[] clientNonce = new byte[32];
        rnd.nextBytes(clientNonce);

        byte[] ballotId = new byte[32];
        rnd.nextBytes(ballotId);

        byte[] ciphertext = TallyKeyService.encryptVote(
                tallyKeyService.getPublicKey(),
                EID,
                candidateId,
                ballotId
        );

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

        VoteRequest req = new VoteRequest(ciphertext, tokenPlain, unblinded, clientNonce);
        voteService.castVote(req);
    }

    @Test
    @DisplayName("Clean tally counts match, invalidCount=0, status=TALLIED")
    void cleanTally() {
        // Cast 5 votes: 2 for A, 2 for B, 1 for C
        castEncryptedVote("v1", candA.getId().intValue());
        castEncryptedVote("v2", candA.getId().intValue());
        castEncryptedVote("v3", candB.getId().intValue());
        castEncryptedVote("v4", candB.getId().intValue());
        castEncryptedVote("v5", candC.getId().intValue());

        List<Shamir.Share> shares = tallyKeyService.getShares().subList(0, 2);

        TallyResult result = tallyService.tally(EID, shares);

        assertNotNull(result);
        assertEquals(0L, result.invalidCount());
        assertEquals(2L, result.counts().get(candA.getId()));
        assertEquals(2L, result.counts().get(candB.getId()));
        assertEquals(1L, result.counts().get(candC.getId()));
        assertEquals(5L, result.closeBlockHeight());
        assertNotNull(result.closeHeadHash());
        assertFalse(result.closeHeadHash().equals(Block.ZERO));
        assertEquals(5, result.ballots().size());

        Election election = electionRepo.findById(EID).orElseThrow();
        assertEquals(Election.Status.TALLIED, election.getStatus());

        // Verify bulletin board can retrieve it
        assertTrue(tallyService.getLatestTally(EID).isPresent());
    }

    @Test
    @DisplayName("Audit dirty before tally blocks tally")
    void auditDirtyBeforeTally() {
        castEncryptedVote("v1", candA.getId().intValue());

        // Corrupt state by deleting DB vote row
        voteRepo.deleteAll();

        List<Shamir.Share> shares = tallyKeyService.getShares().subList(0, 2);

        assertThrows(TallyPreconditionException.class, () -> tallyService.tally(EID, shares));

        Election election = electionRepo.findById(EID).orElseThrow();
        assertEquals(Election.Status.CLOSED, election.getStatus());
    }

    @Test
    @DisplayName("forceNow at CLOSED anchors closeHeadHash to ledger head, "
               + "and closeBlockHeight equals ledger length at close")
    void closeHeadHashAnchoredAtClose() {
        castEncryptedVote("v1", candA.getId().intValue());
        castEncryptedVote("v2", candB.getId().intValue());
        castEncryptedVote("v3", candB.getId().intValue());

        String headBeforeTally = ledgerService.headHash();
        long blocksBeforeTally = ledgerService.blockCount();

        List<Shamir.Share> shares = tallyKeyService.getShares().subList(0, 2);
        TallyResult result = tallyService.tally(EID, shares);

        assertEquals(headBeforeTally, result.closeHeadHash(),
            "closeHeadHash must equal ledger head at time of close (anchor)");
        assertEquals(blocksBeforeTally, result.closeBlockHeight(),
            "closeBlockHeight must equal ledger block count at close");
        assertNotEquals(Block.ZERO, result.closeHeadHash(),
            "closeHeadHash must not be ZERO — checkpoint was anchored");
    }
}
