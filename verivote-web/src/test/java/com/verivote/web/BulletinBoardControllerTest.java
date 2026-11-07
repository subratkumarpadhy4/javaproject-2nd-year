package com.verivote.web;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.crypto.BlindSignatureService.KeyPair;
import com.verivote.core.crypto.Sha256;
import com.verivote.core.crypto.Shamir;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import com.verivote.data.entity.Checkpoint;
import com.verivote.data.entity.Election;
import com.verivote.data.entity.Voter;
import com.verivote.data.repo.CheckpointRepo;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.TokenService;
import com.verivote.service.dto.TokenIssuanceResponse;
import com.verivote.service.dto.VoteRequest;
import com.verivote.service.dto.VoteResponse;
import com.verivote.service.tally.TallyKeyService;
import com.verivote.web.dto.CheckpointResponse;
import com.verivote.web.dto.TallyResponse;
import com.verivote.web.dto.VerifyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BulletinBoardControllerTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private VoterRepo voterRepo;
    @Autowired private VoteRepo voteRepo;
    @Autowired private ElectionRepo electionRepo;
    @Autowired private CheckpointRepo checkpointRepo;
    @Autowired private TokenService tokenService;
    @Autowired private TallyKeyService tallyKeyService;

    private final SecureRandom rnd = new SecureRandom();
    private static final long EID = 1L;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());

        voteRepo.deleteAll();
        checkpointRepo.deleteAll();
        voterRepo.deleteAll();

        Election election = electionRepo.findById(EID).orElseGet(() -> new Election("General Election"));
        election.setStatus(Election.Status.OPEN);
        electionRepo.saveAndFlush(election);
    }

    private VoteResponse castVote(String voterSuffix) {
        Voter voter = new Voter("nid-bb-" + voterSuffix, "Voter " + voterSuffix, "pw", new byte[]{1, 2});
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

        VoteRequest req = new VoteRequest(ciphertext, tokenPlain, unblinded, clientNonce);

        ResponseEntity<VoteResponse> resp = restTemplate.postForEntity(
                "/api/vote",
                req,
                VoteResponse.class
        );
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        return resp.getBody();
    }

    @Test
    @DisplayName("/bb/receipts returns cast vote receipts")
    void getReceipts() {
        VoteResponse v1 = castVote("v1");
        VoteResponse v2 = castVote("v2");

        ResponseEntity<List> resp = restTemplate.getForEntity("/bb/receipts", List.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains(v1.receiptHash()));
        assertTrue(resp.getBody().contains(v2.receiptHash()));
    }

    @Test
    @DisplayName("/bb/verify/{receiptHash} returns NOT_FOUND for unknown receipt")
    void verifyNotFound() {
        ResponseEntity<VerifyResponse> resp = restTemplate.getForEntity("/bb/verify/nonexistenthash", VerifyResponse.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("NOT_FOUND", resp.getBody().status());
    }

    @Test
    @DisplayName("/bb/verify/{receiptHash} returns PENDING_INCLUSION before checkpoint")
    void verifyPendingInclusion() {
        VoteResponse v1 = castVote("v1");

        ResponseEntity<VerifyResponse> resp = restTemplate.getForEntity("/bb/verify/" + v1.receiptHash(), VerifyResponse.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("PENDING_INCLUSION", resp.getBody().status());
    }

    @Test
    @DisplayName("/bb/verify/{receiptHash} returns OK when covered by checkpoint")
    void verifyOkWithCheckpoint() {
        VoteResponse v1 = castVote("v1");

        // Create checkpoint covering this vote
        int treeSize = (int) (v1.blockHeight() + 1);
        Checkpoint cp = new Checkpoint(treeSize, "root-hex", "head-hex", "sig-hex");
        cp.setCreatedAt(Instant.now());
        checkpointRepo.saveAndFlush(cp);

        ResponseEntity<VerifyResponse> resp = restTemplate.getForEntity("/bb/verify/" + v1.receiptHash(), VerifyResponse.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals("OK", resp.getBody().status());
        assertNotNull(resp.getBody().leafData());
        assertNotNull(resp.getBody().proof());
    }

    @Test
    @DisplayName("/bb/checkpoints returns checkpoint list")
    void getCheckpoints() {
        Checkpoint cp = new Checkpoint(10, "root-hex", "head-hex", "sig-hex");
        cp.setCreatedAt(Instant.now());
        checkpointRepo.saveAndFlush(cp);

        ResponseEntity<CheckpointResponse[]> resp = restTemplate.getForEntity("/bb/checkpoints", CheckpointResponse[].class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().length >= 1);
        assertEquals("root-hex", resp.getBody()[0].merkleRoot());
    }

    @Test
    @DisplayName("/bb/tally returns 404 before tally")
    void getTallyNotFoundBeforeTally() {
        ResponseEntity<TallyResponse> resp = restTemplate.getForEntity("/bb/tally?electionId=999", TallyResponse.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }
}
