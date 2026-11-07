package com.verivote.web;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.crypto.BlindSignatureService.KeyPair;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import com.verivote.data.entity.Election;
import com.verivote.data.entity.Voter;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.TokenService;
import com.verivote.service.dto.TokenIssuanceResponse;
import com.verivote.service.dto.VoteRequest;
import com.verivote.service.dto.VoteResponse;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VoteControllerTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private VoterRepo voterRepo;
    @Autowired private ElectionRepo electionRepo;
    @Autowired private TokenService tokenService;

    private final SecureRandom rnd = new SecureRandom();
    private static final long EID = 1L;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());

        Election election = electionRepo.findById(EID).orElseGet(() -> new Election("General Election"));
        election.setStatus(Election.Status.OPEN);
        electionRepo.saveAndFlush(election);
    }

    private VoteRequest buildValidVoteRequest(String voterSuffix) {
        Voter voter = new Voter("nid-w-" + voterSuffix, "Voter " + voterSuffix, "pw", new byte[]{1, 2});
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
    @DisplayName("1. POST /api/vote valid -> 200 + receipt")
    void voteValid() {
        VoteRequest req = buildValidVoteRequest("ctrl1");
        ResponseEntity<VoteResponse> resp = restTemplate.postForEntity("/api/vote", req, VoteResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().receiptHash());
        assertNotNull(resp.getBody().blockHash());
    }

    @Test
    @DisplayName("2. POST /api/vote with invalid signature -> 400")
    void voteInvalidSignature() {
        VoteRequest valid = buildValidVoteRequest("ctrl2");
        byte[] badSig = valid.signature().clone();
        badSig[0] ^= 0x01;

        VoteRequest badReq = new VoteRequest(valid.ciphertext(), valid.tokenPlain(), badSig, valid.clientNonce());
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/vote", badReq, Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
    }

    @Test
    @DisplayName("3. POST /api/vote twice same token -> first 200, second 409")
    void voteTwiceSameToken() {
        VoteRequest req = buildValidVoteRequest("ctrl3");
        ResponseEntity<VoteResponse> resp1 = restTemplate.postForEntity("/api/vote", req, VoteResponse.class);
        assertEquals(HttpStatus.OK, resp1.getStatusCode());

        ResponseEntity<Map> resp2 = restTemplate.postForEntity("/api/vote", req, Map.class);
        assertEquals(HttpStatus.CONFLICT, resp2.getStatusCode());
    }

    @Test
    @DisplayName("4. No auth required (no Authorization header needed)")
    void noAuthRequired() {
        VoteRequest req = buildValidVoteRequest("ctrl4");
        // standard postForEntity has no Authorization header
        ResponseEntity<VoteResponse> resp = restTemplate.postForEntity("/api/vote", req, VoteResponse.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @DisplayName("5. Response body does NOT contain voter id, nonce, or tokenPlain")
    void responseBodyAnonymity() {
        VoteRequest req = buildValidVoteRequest("ctrl5");
        ResponseEntity<String> rawResp = restTemplate.postForEntity("/api/vote", req, String.class);
        assertEquals(HttpStatus.OK, rawResp.getStatusCode());

        String body = rawResp.getBody();
        assertNotNull(body);

        String nonceHex = Hex.encode(req.clientNonce());
        String tokenPlainHex = Hex.encode(req.tokenPlain());

        assertFalse(body.contains(nonceHex), "Response must not contain clientNonce");
        assertFalse(body.contains(tokenPlainHex), "Response must not contain tokenPlain");
        assertFalse(body.toLowerCase().contains("voter"), "Response must not reference voter");
    }
}
