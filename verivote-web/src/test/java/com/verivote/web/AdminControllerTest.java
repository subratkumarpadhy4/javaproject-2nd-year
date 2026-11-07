package com.verivote.web;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.crypto.BlindSignatureService.KeyPair;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.data.entity.Election;
import com.verivote.data.entity.Vote;
import com.verivote.data.entity.Voter;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.OperatorRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.TokenService;
import com.verivote.service.checkpoint.CheckpointSigner;
import com.verivote.service.dto.TokenIssuanceResponse;
import com.verivote.service.dto.VoteRequest;
import com.verivote.service.dto.VoteResponse;
import com.verivote.service.recovery.OperatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import com.verivote.data.repo.SpentTokenRepo;

import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.SpentToken;

import java.time.Instant;
import java.util.Optional;

import com.verivote.data.entity.IssuanceCounter;
import com.verivote.data.repo.IssuanceCounterRepo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminControllerTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private VoteRepo voteRepo;
    @Autowired private SpentTokenRepo spentTokenRepo;
    @Autowired private VoterRepo voterRepo;
    @Autowired private ElectionRepo electionRepo;
    @Autowired private TokenService tokenService;
    @Autowired private OperatorService operatorService;
    @Autowired private OperatorRepo operatorRepo;
    @Autowired private LedgerService ledgerService;
    @Autowired private IssuanceCounterRepo issuanceCounterRepo;

    private final SecureRandom rnd = new SecureRandom();
    private static final long EID = 1L;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
        Election election = electionRepo.findById(EID).orElseGet(() -> new Election("General Election"));
        election.setStatus(Election.Status.OPEN);
        electionRepo.saveAndFlush(election);

        // Synchronize in-memory H2 DB with any existing ledger blocks on disk from previous tests
        for (long h = 0; h < ledgerService.blockCount(); h++) {
            Optional<Block> opt = ledgerService.at(h);
            if (opt.isPresent()) {
                Block b = opt.get();
                if (Block.VOTE.equals(b.type())) {
                    if (voteRepo.findByBlockHeight(b.height()).isEmpty()) {
                        Vote v = new Vote(EID, b.tokenHash(), b.receiptHash(), b.ciphertextB64(), b.height(), b.blockHash());
                        voteRepo.saveAndFlush(v);
                    }
                    if (!spentTokenRepo.existsById(b.tokenHash())) {
                        spentTokenRepo.saveAndFlush(new SpentToken(b.tokenHash(), EID));
                    }
                }
            }
        }

        IssuanceCounter counter = issuanceCounterRepo.findById(EID).orElseGet(() -> new IssuanceCounter(EID, 0L, 0L));
        if (counter.getIssued() < ledgerService.realVoteCount() + 10) {
            counter.setIssued(ledgerService.realVoteCount() + 10);
            issuanceCounterRepo.saveAndFlush(counter);
        }
    }

    private VoteRequest buildValidVoteRequest(String voterSuffix) {
        Voter voter = new Voter("nid-adm-" + voterSuffix, "Voter " + voterSuffix, "pw", new byte[]{1, 2});
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
    @Order(1)
    @DisplayName("1. GET /api/admin/audit on clean state -> 200, clean=true")
    void auditClean() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/api/admin/audit", String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("\"clean\":true"), "Audit report must be clean initially. Got: " + resp.getBody());
    }

    @Test
    @Order(2)
    @DisplayName("2. GET /api/admin/audit after corrupting vote row -> 200, clean=false, DB_CIPHERTEXT_MISMATCH")
    void auditCorruptedVote() {
        VoteRequest req = buildValidVoteRequest("corrupt");
        ResponseEntity<VoteResponse> voteResp = restTemplate.postForEntity("/api/vote", req, VoteResponse.class);
        assertEquals(HttpStatus.OK, voteResp.getStatusCode());

        List<Vote> votes = voteRepo.findAll();
        assertFalse(votes.isEmpty());
        Vote lastVote = votes.get(votes.size() - 1);
        String originalCt = lastVote.getEncryptedVote();

        try {
            lastVote.setEncryptedVote("AAAA");
            voteRepo.saveAndFlush(lastVote);

            ResponseEntity<String> resp = restTemplate.getForEntity("/api/admin/audit", String.class);
            assertEquals(HttpStatus.OK, resp.getStatusCode());
            assertNotNull(resp.getBody());
            assertTrue(resp.getBody().contains("\"clean\":false"));
            assertTrue(resp.getBody().contains("DB_CIPHERTEXT_MISMATCH"));
        } finally {
            // Restore original ciphertext to leave clean DB
            lastVote.setEncryptedVote(originalCt);
            voteRepo.saveAndFlush(lastVote);
        }
    }

    @Test
    @Order(3)
    @DisplayName("3. GET /api/admin/ledger-status -> 200 with blockCount, headHash")
    void ledgerStatus() {
        ResponseEntity<String> resp = restTemplate.getForEntity("/api/admin/ledger-status", String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("blockCount"));
        assertTrue(resp.getBody().contains("headHash"));
    }

    @Test
    @Order(4)
    @DisplayName("4. POST /api/admin/operators -> 200, then GET /api/admin/operators returns it")
    void enrollAndListOperators() {
        java.security.KeyPair kp = CheckpointSigner.generateKeyPair();
        String pubB64 = CheckpointSigner.encodePublicKey(kp);
        String opName = "op-enrolled-" + System.currentTimeMillis();

        Map<String, String> body = Map.of(
                "name", opName,
                "ed25519Pub", pubB64,
                "role", "AUDIT",
                "createdBy", "admin"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, String>> postReq = new HttpEntity<>(body, headers);

        ResponseEntity<String> postResp = restTemplate.postForEntity("/api/admin/operators", postReq, String.class);
        assertEquals(HttpStatus.OK, postResp.getStatusCode());
        assertNotNull(postResp.getBody());
        assertTrue(postResp.getBody().contains(opName));

        ResponseEntity<String> getResp = restTemplate.getForEntity("/api/admin/operators", String.class);
        assertEquals(HttpStatus.OK, getResp.getStatusCode());
        assertNotNull(getResp.getBody());
        assertTrue(getResp.getBody().contains(opName));
        assertTrue(getResp.getBody().contains("AUDIT"));
    }

    @Test
    @Order(5)
    @DisplayName("5. POST /api/admin/recover with invalid signature -> 400 with code SIGNATURE_INVALID")
    void recoverInvalidSignature() {
        VoteRequest vreq = buildValidVoteRequest("rec");
        ResponseEntity<VoteResponse> voteResp = restTemplate.postForEntity("/api/vote", vreq, VoteResponse.class);
        assertEquals(HttpStatus.OK, voteResp.getStatusCode());
        assertNotNull(voteResp.getBody());
        long blockHeight = voteResp.getBody().blockHeight();
        String blockHash = voteResp.getBody().blockHash();
        Vote v = voteRepo.findAll().stream().filter(x -> x.getBlockHeight() == blockHeight).findFirst().orElseThrow();
        String tokenHash = v.getTokenHash();

        Election election = electionRepo.findById(EID).orElseThrow();
        election.setStatus(Election.Status.CLOSED);
        electionRepo.saveAndFlush(election);

        try {
            // Delete spentToken so ACCEPT_AS_ORPHAN precondition passes
            spentTokenRepo.deleteById(tokenHash);
            spentTokenRepo.flush();

            String op1 = "op-rec1-" + System.currentTimeMillis();
            String op2 = "op-rec2-" + System.currentTimeMillis();
            java.security.KeyPair kp1 = CheckpointSigner.generateKeyPair();
            java.security.KeyPair kp2 = CheckpointSigner.generateKeyPair();

            operatorService.enroll(op1, CheckpointSigner.encodePublicKey(kp1), "AUDIT", "admin");
            operatorService.enroll(op2, CheckpointSigner.encodePublicKey(kp2), "AUDIT", "admin");

            Map<String, Object> body = new HashMap<>();
            body.put("blockHeight", blockHeight);
            body.put("action", "ACCEPT_AS_ORPHAN");
            body.put("reason", "testing bad sig");
            body.put("blockHash", blockHash);
            body.put("tokenHash", tokenHash);
            body.put("operator1", op1);
            body.put("signature1", Base64Codec.encode(new byte[64]));
            body.put("operator2", op2);
            body.put("signature2", Base64Codec.encode(new byte[64]));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> postReq = new HttpEntity<>(body, headers);

            ResponseEntity<String> resp = restTemplate.postForEntity("/api/admin/recover", postReq, String.class);
            assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
            assertNotNull(resp.getBody());
            assertTrue(resp.getBody().contains("SIGNATURE_INVALID"),
                    "Expected SIGNATURE_INVALID error code in body: " + resp.getBody());
        } finally {
            election.setStatus(Election.Status.OPEN);
            electionRepo.saveAndFlush(election);
        }
    }
}
