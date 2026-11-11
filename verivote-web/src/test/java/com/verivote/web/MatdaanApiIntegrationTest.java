package com.verivote.web;

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
@SuppressWarnings({"unchecked", "rawtypes"})
class MatdaanApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    @DisplayName("Verify Full Matdaan Civic Flow: Registration -> Login -> Vote -> Metrics -> Admin Tally")
    void testMatdaanCivicFlow() {
        // 1. GET /api/metrics
        ResponseEntity<Map> metricsResp = restTemplate.getForEntity("/api/metrics", Map.class);
        assertEquals(HttpStatus.OK, metricsResp.getStatusCode());
        assertNotNull(metricsResp.getBody());
        assertTrue(metricsResp.getBody().containsKey("registered"));
        assertTrue(metricsResp.getBody().containsKey("votes"));

        // 2. POST /api/register - Minor rejected
        ResponseEntity<Map> minorResp = restTemplate.postForEntity("/api/register",
                Map.of("name", "Young Voter", "age", 17, "state", "Delhi", "pin", "1234"), Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, minorResp.getStatusCode());
        assertTrue(minorResp.getBody().get("error").toString().contains("18 years"));

        // 3. POST /api/register - Valid 18+ elector
        ResponseEntity<Map> regResp = restTemplate.postForEntity("/api/register",
                Map.of("name", "Arun Kumar", "age", 24, "state", "Odisha", "pin", "4321"), Map.class);
        assertEquals(HttpStatus.CREATED, regResp.getStatusCode());
        Map regData = regResp.getBody();
        assertNotNull(regData);
        assertEquals(true, regData.get("success"));
        String voterId = (String) regData.get("voterId");
        assertNotNull(voterId);
        assertTrue(voterId.startsWith("VTR"));

        // 4. POST /api/login - Wrong PIN -> 401
        ResponseEntity<Map> wrongPinResp = restTemplate.postForEntity("/api/login",
                Map.of("voterId", voterId, "pin", "9999"), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, wrongPinResp.getStatusCode());

        // 5. POST /api/login - Correct credentials -> 200
        ResponseEntity<Map> loginResp = restTemplate.postForEntity("/api/login",
                Map.of("voterId", voterId, "pin", "4321"), Map.class);
        assertEquals(HttpStatus.OK, loginResp.getStatusCode());
        Map loginData = loginResp.getBody();
        assertEquals(true, loginData.get("success"));
        assertEquals("Arun Kumar", loginData.get("name"));
        assertEquals(false, loginData.get("hasVoted"));

        String sessionToken = (String) loginData.get("sessionToken");
        assertNotNull(sessionToken);

        // 6. Unauthenticated ballot rejected
        ResponseEntity<Map> anonVote = restTemplate.postForEntity("/api/vote",
                Map.of("partyCode", "BJP"), Map.class);
        assertEquals(HttpStatus.UNAUTHORIZED, anonVote.getStatusCode());

        // 7. Ballot candidates come from the database
        ResponseEntity<Map> candResp = restTemplate.getForEntity("/api/candidates", Map.class);
        assertEquals(HttpStatus.OK, candResp.getStatusCode());
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) candResp.getBody().get("candidates");
        Map<String, Object> bjp = candidates.stream()
                .filter(c -> "BJP".equals(c.get("shortName"))).findFirst().orElseThrow();
        assertEquals("NOTA", candidates.get(candidates.size() - 1).get("shortName"));

        // 8. Cast ballot with voter session
        HttpHeaders voterHeaders = new HttpHeaders();
        voterHeaders.setBearerAuth(sessionToken);
        ResponseEntity<Map> voteResp = restTemplate.exchange("/api/vote", HttpMethod.POST,
                new HttpEntity<>(Map.of("candidateId", bjp.get("id")), voterHeaders), Map.class);
        assertEquals(HttpStatus.OK, voteResp.getStatusCode());
        Map voteData = voteResp.getBody();
        assertEquals(true, voteData.get("success"));
        String receiptHash = (String) voteData.get("receiptHash");
        assertNotNull(receiptHash);

        // 9. Re-voting rejected
        ResponseEntity<Map> revoteResp = restTemplate.exchange("/api/vote", HttpMethod.POST,
                new HttpEntity<>(Map.of("partyCode", "INC"), voterHeaders), Map.class);
        assertEquals(HttpStatus.CONFLICT, revoteResp.getStatusCode());

        // 10. /api/me reflects the vote
        ResponseEntity<Map> meResp = restTemplate.exchange("/api/me", HttpMethod.GET,
                new HttpEntity<>(voterHeaders), Map.class);
        assertEquals(true, meResp.getBody().get("hasVoted"));

        // 11. Receipt is on the public bulletin board
        ResponseEntity<Map> verifyResp = restTemplate.getForEntity("/bb/verify/" + receiptHash, Map.class);
        assertEquals(HttpStatus.OK, verifyResp.getStatusCode());

        // 12. Admin tally: fake/legacy tokens refused, real officer login accepted
        HttpHeaders legacy = new HttpHeaders();
        legacy.set("Authorization", "Bearer admin123");
        assertEquals(HttpStatus.FORBIDDEN, restTemplate.exchange("/api/admin/tally",
                HttpMethod.GET, new HttpEntity<>(legacy), Map.class).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, restTemplate.postForEntity("/api/admin/login",
                Map.of("username", "admin", "password", "wrong"), Map.class).getStatusCode());

        ResponseEntity<Map> adminLogin = restTemplate.postForEntity("/api/admin/login",
                Map.of("username", "admin", "password", "admin123"), Map.class);
        assertEquals(HttpStatus.OK, adminLogin.getStatusCode());
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth((String) adminLogin.getBody().get("token"));

        ResponseEntity<Map> tallyResp = restTemplate.exchange("/api/admin/tally",
                HttpMethod.GET, new HttpEntity<>(adminHeaders), Map.class);
        assertEquals(HttpStatus.OK, tallyResp.getStatusCode());
        Map tallyData = tallyResp.getBody();
        assertTrue(((Number) tallyData.get("registered")).longValue() >= 1);
        assertTrue(((Number) tallyData.get("votes")).longValue() >= 1);
        List<Map<String, Object>> tally = (List<Map<String, Object>>) tallyData.get("tally");
        Map<String, Object> bjpRow = tally.stream()
                .filter(r -> "BJP".equals(r.get("shortName"))).findFirst().orElseThrow();
        assertTrue(((Number) bjpRow.get("votes")).longValue() >= 1, "decrypted tally must count the BJP ballot");
        assertEquals(0, ((Number) tallyData.get("undecryptable")).intValue());
    }
}
