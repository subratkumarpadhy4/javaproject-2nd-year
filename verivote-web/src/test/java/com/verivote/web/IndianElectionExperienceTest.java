package com.verivote.web;

import com.verivote.data.entity.Candidate;
import com.verivote.data.entity.Election;
import com.verivote.data.repo.CandidateRepo;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.data.repo.VoterRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IndianElectionExperienceTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ElectionRepo electionRepo;
    @Autowired private CandidateRepo candidateRepo;
    @Autowired private VoteRepo voteRepo;
    @Autowired private VoterRepo voterRepo;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    @DisplayName("Validation: Complete Indian Election Citizen Flow, Admin Governance & Verification")
    void testCompleteIndianElectionExperience() {
        // 1. GET /api/web/election -> returns Indian political parties and open status
        ResponseEntity<Map> elResp = restTemplate.getForEntity("/api/web/election", Map.class);
        assertEquals(HttpStatus.OK, elResp.getStatusCode());
        Map elData = elResp.getBody();
        assertNotNull(elData);
        assertEquals("OPEN", elData.get("status"));
        assertTrue(elData.get("notice").toString().contains("Independent Educational Voting Demonstration"));

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) elData.get("candidates");
        assertNotNull(candidates);
        assertTrue(candidates.size() >= 6, "Expected at least 6 Indian political party options");

        boolean hasBjp = candidates.stream().anyMatch(c -> c.get("party").toString().contains("Bharatiya Janata Party"));
        boolean hasTdp = candidates.stream().anyMatch(c -> c.get("party").toString().contains("Telugu Desam Party"));
        boolean hasInc = candidates.stream().anyMatch(c -> c.get("party").toString().contains("Indian National Congress"));
        boolean hasYsrcp = candidates.stream().anyMatch(c -> c.get("party").toString().contains("Yuvajana Sramika Rythu"));
        boolean hasJdu = candidates.stream().anyMatch(c -> c.get("party").toString().contains("Janata Dal"));
        boolean hasAap = candidates.stream().anyMatch(c -> c.get("party").toString().contains("Aam Aadmi Party"));

        assertTrue(hasBjp, "Missing BJP");
        assertTrue(hasTdp, "Missing TDP");
        assertTrue(hasInc, "Missing INC");
        assertTrue(hasYsrcp, "Missing YSRCP");
        assertTrue(hasJdu, "Missing JD(U)");
        assertTrue(hasAap, "Missing AAP");

        // 2. Citizen Identification: Demo voter ID auth
        String demoId = "DEMO-IND-TEST-99";
        ResponseEntity<Map> authResp = restTemplate.postForEntity("/api/web/demo-auth",
                Map.of("demoVoterId", demoId), Map.class);
        assertEquals(HttpStatus.OK, authResp.getStatusCode());
        Map authData = authResp.getBody();
        assertNotNull(authData);
        String sessionToken = (String) authData.get("sessionToken");
        assertNotNull(sessionToken);

        // 3. Blind Token Issuance
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setBearerAuth(sessionToken);
        ResponseEntity<Map> tokenResp = restTemplate.exchange("/api/web/request-token",
                HttpMethod.POST, new HttpEntity<>(Map.of(), tokenHeaders), Map.class);
        assertEquals(HttpStatus.OK, tokenResp.getStatusCode());
        Map tokenData = tokenResp.getBody();
        assertNotNull(tokenData);
        String tokenPlain = (String) tokenData.get("tokenPlain");
        String signature = (String) tokenData.get("signature");
        assertNotNull(tokenPlain);
        assertNotNull(signature);

        // 4. Cast Demonstration Ballot for BJP (first candidate)
        int targetCandidateId = ((Number) candidates.get(0).get("id")).intValue();
        long initialVoteCount = voteRepo.count();

        Map<String, Object> castPayload = Map.of(
                "candidateId", targetCandidateId,
                "tokenPlain", tokenPlain,
                "signature", signature
        );
        ResponseEntity<Map> castResp = restTemplate.postForEntity("/api/web/cast-vote", castPayload, Map.class);
        assertEquals(HttpStatus.OK, castResp.getStatusCode());
        Map castData = castResp.getBody();
        assertNotNull(castData);
        String receiptHash = (String) castData.get("receiptHash");
        assertNotNull(receiptHash);
        assertEquals(64, receiptHash.length());

        // 5. Verify Vote Persistence in DB & Ledger
        assertEquals(initialVoteCount + 1, voteRepo.count(), "Vote record must be persisted in database");
        assertTrue(voteRepo.findByReceiptHash(receiptHash).isPresent());

        // 6. Verify Replay Prevention: Refresh/resubmit with same token must fail
        ResponseEntity<Map> replayResp = restTemplate.postForEntity("/api/web/cast-vote", castPayload, Map.class);
        assertNotEquals(HttpStatus.OK, replayResp.getStatusCode(), "Replay submission must be rejected");

        // 7. Verify Vote Verification endpoint
        ResponseEntity<Map> verifyResp = restTemplate.getForEntity("/api/web/verify/" + receiptHash, Map.class);
        assertEquals(HttpStatus.OK, verifyResp.getStatusCode());
        Map verifyData = verifyResp.getBody();
        assertNotNull(verifyData);
        assertTrue("VERIFIED".equals(verifyData.get("status")) || "PENDING_INCLUSION".equals(verifyData.get("status")));

        // 8. Admin Statistics: Verified to come from actual stored records
        ResponseEntity<Map> statsResp = restTemplate.getForEntity("/api/admin/stats", Map.class);
        assertEquals(HttpStatus.OK, statsResp.getStatusCode());
        Map statsData = statsResp.getBody();
        assertNotNull(statsData);
        assertTrue(((Number) statsData.get("castVotes")).longValue() >= 1);
        assertTrue(((Number) statsData.get("registeredVoters")).longValue() >= 1);

        // 9. Admin Submitted Votes List
        ResponseEntity<List> votesResp = restTemplate.getForEntity("/api/admin/votes", List.class);
        assertEquals(HttpStatus.OK, votesResp.getStatusCode());
        List votesList = votesResp.getBody();
        assertNotNull(votesList);
        assertFalse(votesList.isEmpty());

        // 10. Admin Election Gate: Close voting
        ResponseEntity<Map> closeResp = restTemplate.postForEntity("/api/admin/election/status",
                Map.of("status", "CLOSED"), Map.class);
        assertEquals(HttpStatus.OK, closeResp.getStatusCode());

        // Test that voting is now rejected when CLOSED
        // Register another demo voter
        ResponseEntity<Map> auth2Resp = restTemplate.postForEntity("/api/web/demo-auth",
                Map.of("demoVoterId", "DEMO-IND-TEST-100"), Map.class);
        String token2Session = (String) auth2Resp.getBody().get("sessionToken");
        HttpHeaders token2Headers = new HttpHeaders();
        token2Headers.setBearerAuth(token2Session);
        ResponseEntity<Map> token2Resp = restTemplate.exchange("/api/web/request-token",
                HttpMethod.POST, new HttpEntity<>(Map.of(), token2Headers), Map.class);
        String token2Plain = (String) token2Resp.getBody().get("tokenPlain");
        String sig2 = (String) token2Resp.getBody().get("signature");

        ResponseEntity<Map> blockedCastResp = restTemplate.postForEntity("/api/web/cast-vote",
                Map.of("candidateId", targetCandidateId, "tokenPlain", token2Plain, "signature", sig2),
                Map.class);
        assertEquals(HttpStatus.FORBIDDEN, blockedCastResp.getStatusCode(), "Voting must be blocked when election is CLOSED");

        // Reopen election
        restTemplate.postForEntity("/api/admin/election/status", Map.of("status", "OPEN"), Map.class);

        // 11. Admin Candidate Management: Add and Remove Party
        ResponseEntity<Candidate> addCandResp = restTemplate.postForEntity("/api/admin/candidates",
                Map.of("party", "Dravida Munnetra Kazhagam (DMK)", "name", "Demo Candidate", "constituency", "Chennai Central", "symbol", "dmk"),
                Candidate.class);
        assertEquals(HttpStatus.OK, addCandResp.getStatusCode());
        Candidate addedCand = addCandResp.getBody();
        assertNotNull(addedCand);
        assertNotNull(addedCand.getId());

        // Delete candidate
        restTemplate.delete("/api/admin/candidates/" + addedCand.getId());
        assertFalse(candidateRepo.existsById(addedCand.getId()));
    }
}
