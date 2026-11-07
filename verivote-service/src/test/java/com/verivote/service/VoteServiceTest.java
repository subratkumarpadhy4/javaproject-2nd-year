package com.verivote.service;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.crypto.BlindSignatureService.KeyPair;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.FileLedgerService;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.Election;
import com.verivote.data.entity.Vote;
import com.verivote.data.entity.Voter;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.SpentTokenRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.dto.TokenIssuanceResponse;
import com.verivote.service.dto.VoteRequest;
import com.verivote.service.dto.VoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ServiceTestApplication.class)
@TestPropertySource(locations = "classpath:application-service.properties")
class VoteServiceTest {

    @Autowired private VoteRepo voteRepo;
    @Autowired private SpentTokenRepo spentTokenRepo;
    @Autowired private ElectionRepo electionRepo;
    @Autowired private VoterRepo voterRepo;
    @Autowired private TokenService tokenService;
    @Autowired private PlatformTransactionManager txManager;

    @TempDir
    Path tempDir;

    private LedgerService ledgerService;
    private VoteService voteService;
    private Path ledgerFile;
    private final SecureRandom rnd = new SecureRandom();
    private static final long EID = 1L;

    @BeforeEach
    void setUp() throws IOException {
        ledgerFile = tempDir.resolve("test_vote_ledger.ndjson");
        ledgerService = new FileLedgerService(ledgerFile);
        voteService = new VoteService(ledgerService, voteRepo, spentTokenRepo, electionRepo, tokenService, txManager);

        Election election = electionRepo.findById(EID).orElseGet(() -> new Election("Test General Election"));
        election.setStatus(Election.Status.OPEN);
        electionRepo.saveAndFlush(election);
    }

    private VoteRequest buildValidVoteRequest(String voterSuffix) {
        Voter voter = new Voter("nid-v-" + voterSuffix, "Voter " + voterSuffix, "pw", new byte[]{1, 2});
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

    @Test
    @DisplayName("1. Happy path: valid vote -> returns receiptHash + blockHeight + blockHash")
    void happyPathValidVote() {
        long spentCountBefore = spentTokenRepo.count();
        long voteCountBefore = voteRepo.count();
        long ledgerCountBefore = ledgerService.blockCount();

        VoteRequest req = buildValidVoteRequest("hp1");
        VoteResponse resp = voteService.castVote(req);

        assertNotNull(resp);
        assertNotNull(resp.receiptHash());
        assertEquals(ledgerCountBefore, resp.blockHeight());
        assertNotNull(resp.blockHash());

        assertEquals(ledgerCountBefore + 1, ledgerService.blockCount());
        assertEquals(spentCountBefore + 1, spentTokenRepo.count());
        assertEquals(voteCountBefore + 1, voteRepo.count());
    }

    @Test
    @DisplayName("2. Invalid signature (tamper 1 byte) -> IllegalArgumentException, ledger unchanged, no rows inserted")
    void invalidSignatureFails() {
        VoteRequest validReq = buildValidVoteRequest("is1");
        byte[] tamperedSig = validReq.signature().clone();
        tamperedSig[0] ^= 0x01;

        VoteRequest badReq = new VoteRequest(validReq.ciphertext(), validReq.tokenPlain(), tamperedSig, validReq.clientNonce());

        long ledgerBefore = ledgerService.blockCount();
        long spentBefore = spentTokenRepo.count();
        long voteBefore = voteRepo.count();

        assertThrows(IllegalArgumentException.class, () -> voteService.castVote(badReq));

        assertEquals(ledgerBefore, ledgerService.blockCount());
        assertEquals(spentBefore, spentTokenRepo.count());
        assertEquals(voteBefore, voteRepo.count());
    }

    @Test
    @DisplayName("3. Same token cast twice -> first succeeds, second throws DataIntegrityViolationException")
    void doubleSpendSameTokenThrows() {
        VoteRequest req = buildValidVoteRequest("ds1");
        voteService.castVote(req);

        // Cast same token again
        assertThrows(DataIntegrityViolationException.class, () -> voteService.castVote(req));
    }

    @Test
    @DisplayName("4. Election closed -> IllegalStateException")
    void electionClosedThrows() {
        Election election = electionRepo.findById(EID).orElseThrow();
        election.setStatus(Election.Status.CLOSED);
        electionRepo.saveAndFlush(election);

        VoteRequest req = buildValidVoteRequest("ec1");
        assertThrows(IllegalStateException.class, () -> voteService.castVote(req));
    }

    @Test
    @DisplayName("5. CONCURRENT double-spend: 20 threads race to cast the SAME token")
    void concurrentDoubleSpend() throws InterruptedException {
        VoteRequest req = buildValidVoteRequest("concurrent");

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        long spentBefore = spentTokenRepo.count();
        long voteBefore = voteRepo.count();
        long ledgerBefore = ledgerService.blockCount();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    voteService.castVote(req);
                    successCount.incrementAndGet();
                } catch (DataIntegrityViolationException e) {
                    duplicateCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly 1 thread must succeed");
        assertEquals(19, duplicateCount.get(), "Exactly 19 threads must fail with DataIntegrityViolationException");
        assertEquals(spentBefore + 1, spentTokenRepo.count(), "Exactly 1 spent_token row inserted");
        assertEquals(voteBefore + 1, voteRepo.count(), "Exactly 1 vote row inserted");
        assertEquals(ledgerBefore + 1, ledgerService.blockCount(), "Exactly 1 VOTE block in ledger");
        assertEquals(0L, ledgerService.orphanCount(), "ledger.orphanCount() == 0");
    }

    @Test
    @DisplayName("6. Crash-after-append simulation: rollback fires afterCompletion to markOrphan")
    void crashAfterAppendRollbackMarksOrphan() {
        VoteRepo throwingVoteRepo = new VoteRepo() {
            @Override public <S extends Vote> S save(S entity) { throw new RuntimeException("Simulated crash after append"); }
            @Override public <S extends Vote> S saveAndFlush(S entity) { throw new RuntimeException("Simulated crash after append"); }
            @Override public java.util.Optional<Vote> findById(Long aLong) { return voteRepo.findById(aLong); }
            @Override public boolean existsById(Long aLong) { return voteRepo.existsById(aLong); }
            @Override public java.util.List<Vote> findAll() { return voteRepo.findAll(); }
            @Override public java.util.List<Vote> findAllById(Iterable<Long> longs) { return voteRepo.findAllById(longs); }
            @Override public long count() { return voteRepo.count(); }
            @Override public void deleteById(Long aLong) { voteRepo.deleteById(aLong); }
            @Override public void delete(Vote entity) { voteRepo.delete(entity); }
            @Override public void deleteAllById(Iterable<? extends Long> longs) { voteRepo.deleteAllById(longs); }
            @Override public void deleteAll(Iterable<? extends Vote> entities) { voteRepo.deleteAll(entities); }
            @Override public void deleteAll() { voteRepo.deleteAll(); }
            @Override public void flush() { voteRepo.flush(); }
            @Override public <S extends Vote> java.util.List<S> saveAllAndFlush(Iterable<S> entities) { return voteRepo.saveAllAndFlush(entities); }
            @Override public void deleteAllInBatch(Iterable<Vote> entities) { voteRepo.deleteAllInBatch(entities); }
            @Override public void deleteAllByIdInBatch(Iterable<Long> longs) { voteRepo.deleteAllByIdInBatch(longs); }
            @Override public void deleteAllInBatch() { voteRepo.deleteAllInBatch(); }
            @Override public Vote getOne(Long aLong) { return voteRepo.getOne(aLong); }
            @Override public Vote getById(Long aLong) { return voteRepo.getById(aLong); }
            @Override public Vote getReferenceById(Long aLong) { return voteRepo.getReferenceById(aLong); }
            @Override public <S extends Vote> java.util.Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return voteRepo.findOne(example); }
            @Override public <S extends Vote> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example) { return voteRepo.findAll(example); }
            @Override public <S extends Vote> java.util.List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return voteRepo.findAll(example, sort); }
            @Override public <S extends Vote> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return voteRepo.findAll(example, pageable); }
            @Override public <S extends Vote> long count(org.springframework.data.domain.Example<S> example) { return voteRepo.count(example); }
            @Override public <S extends Vote> boolean exists(org.springframework.data.domain.Example<S> example) { return voteRepo.exists(example); }
            @Override public <S extends Vote, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return voteRepo.findBy(example, queryFunction); }
            @Override public <S extends Vote> java.util.List<S> saveAll(Iterable<S> entities) { return voteRepo.saveAll(entities); }
            @Override public java.util.List<Vote> findAll(org.springframework.data.domain.Sort sort) { return voteRepo.findAll(sort); }
            @Override public org.springframework.data.domain.Page<Vote> findAll(org.springframework.data.domain.Pageable pageable) { return voteRepo.findAll(pageable); }
            @Override public java.util.Optional<Vote> findByReceiptHash(String receiptHash) { return voteRepo.findByReceiptHash(receiptHash); }
            @Override public java.util.Optional<Vote> findByBlockHeight(Long blockHeight) { return voteRepo.findByBlockHeight(blockHeight); }
        };

        VoteService crashVoteService = new VoteService(ledgerService, throwingVoteRepo, spentTokenRepo, electionRepo, tokenService, txManager);

        VoteRequest req = buildValidVoteRequest("crash1");
        long ledgerBefore = ledgerService.blockCount();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> crashVoteService.castVote(req));
        assertTrue(ex.getMessage().contains("Simulated crash after append"));

        // Verify: ledger has the appended block, plus afterCompletion fired markOrphan -> orphanCount == 1
        assertEquals(1L, ledgerService.orphanCount(), "afterCompletion must fire markOrphan on rollback");
        assertTrue(ledgerService.isOrphaned(ledgerBefore));
        assertEquals(0L, ledgerService.realVoteCount());
    }

    @Test
    @DisplayName("7. clientNonce is never stored in ledger or vote table")
    void clientNonceNeverStored() throws IOException {
        VoteRequest req = buildValidVoteRequest("nonce1");
        String nonceHex = Hex.encode(req.clientNonce());

        VoteResponse resp = voteService.castVote(req);

        // Check ledger file
        String ledgerContent = Files.readString(ledgerFile, StandardCharsets.UTF_8);
        assertFalse(ledgerContent.contains(nonceHex), "Ledger file must NOT contain clientNonce");

        // Check vote row
        Vote vote = voteRepo.findByReceiptHash(resp.receiptHash()).orElseThrow();
        assertFalse(vote.getEncryptedVote().contains(nonceHex));
        assertFalse(vote.getTokenHash().contains(nonceHex));
    }
}
