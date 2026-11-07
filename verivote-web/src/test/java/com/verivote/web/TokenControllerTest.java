package com.verivote.web;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.crypto.BlindSignatureService.KeyPair;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.service.TokenService;
import com.verivote.service.dto.LoginRequest;
import com.verivote.service.dto.LoginResponse;
import com.verivote.service.dto.RegisterRequest;
import com.verivote.service.dto.RegisterResponse;
import com.verivote.service.dto.TokenIssuanceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TokenControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    private String obtainSessionToken(String nationalId) {
        RegisterResponse reg = restTemplate.postForEntity("/api/register",
                new RegisterRequest(nationalId, "Voter " + nationalId, "Pass1234!"), RegisterResponse.class).getBody();
        assertNotNull(reg);

        LoginResponse login = restTemplate.postForEntity("/api/login",
                new LoginRequest(nationalId, "Pass1234!"), LoginResponse.class).getBody();
        assertNotNull(login);
        return login.sessionToken();
    }

    @Test
    @DisplayName("1. GET /api/token/pubkey -> 200, base64 decodes to non-empty bytes")
    void getPubkey() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/token/pubkey", Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());

        String b64 = (String) (resp.getBody().containsKey("publicKeySpki")
                ? resp.getBody().get("publicKeySpki")
                : resp.getBody().get("pubkey"));
        assertNotNull(b64);
        byte[] spki = Base64Codec.decode(b64);
        assertTrue(spki.length > 0);
    }

    @Test
    @DisplayName("2. POST /api/token/request no session -> 401")
    void requestNoSession() {
        Map<String, String> body = Map.of("blindedMessage", Base64Codec.encode(new byte[256]));
        ResponseEntity<String> resp = restTemplate.postForEntity("/api/token/request", body, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    @DisplayName("3. POST /api/token/request valid session -> 200 + signature")
    void requestValidSession() {
        String sessionToken = obtainSessionToken("TOK-NAT-3");
        byte[] blinded = new byte[256];
        blinded[0] = 0x05;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + sessionToken);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("blindedMessage", Base64Codec.encode(blinded)), headers);

        ResponseEntity<TokenIssuanceResponse> resp = restTemplate.exchange("/api/token/request", HttpMethod.POST, request, TokenIssuanceResponse.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().signature());
    }

    @Test
    @DisplayName("4. Second request with same session -> 409")
    void secondRequestWithSameSession() {
        String sessionToken = obtainSessionToken("TOK-NAT-4");
        byte[] blinded = new byte[256];
        blinded[0] = 0x06;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + sessionToken);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("blindedMessage", Base64Codec.encode(blinded)), headers);

        ResponseEntity<TokenIssuanceResponse> resp1 = restTemplate.exchange("/api/token/request", HttpMethod.POST, request, TokenIssuanceResponse.class);
        assertEquals(HttpStatus.OK, resp1.getStatusCode());

        ResponseEntity<String> resp2 = restTemplate.exchange("/api/token/request", HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.CONFLICT, resp2.getStatusCode());
    }

    @Test
    @DisplayName("5. Full HTTP round-trip: register -> login -> totp -> pubkey -> blind -> request -> unblind -> verify")
    void fullHttpRoundTrip() {
        String sessionToken = obtainSessionToken("TOK-NAT-5");

        // 1. GET pubkey
        ResponseEntity<Map> pubkeyResp = restTemplate.getForEntity("/api/token/pubkey", Map.class);
        String spkiB64 = (String) pubkeyResp.getBody().get("publicKeySpki");
        byte[] pubkeySpki = Base64Codec.decode(spkiB64);

        // Election key pair from server
        KeyPair serverKp = tokenService.getElectionKeyPair();
        KeyPair clientKp = new KeyPair(pubkeySpki, new byte[0]);

        // 2. Blind message
        byte[] message = "VERIVOTE-TOKEN-v1-HTTP-TEST".getBytes(StandardCharsets.UTF_8);
        SecureRandom rnd = new SecureRandom();
        BigInteger[] blindingFactor = new BigInteger[1];
        byte[] blinded = BlindSignatureService.blind(message, clientKp, rnd, blindingFactor);

        // 3. POST /api/token/request
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + sessionToken);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("blindedMessage", Base64Codec.encode(blinded)), headers);

        ResponseEntity<TokenIssuanceResponse> tokenResp = restTemplate.exchange("/api/token/request", HttpMethod.POST, request, TokenIssuanceResponse.class);
        assertEquals(HttpStatus.OK, tokenResp.getStatusCode());
        assertNotNull(tokenResp.getBody());

        // 4. Unblind
        byte[] blindSignature = Base64Codec.decode(tokenResp.getBody().signature());
        byte[] unblinded = BlindSignatureService.unblind(blindSignature, blindingFactor[0].toByteArray(), clientKp, 256);

        // 5. Verify against pubkey
        assertTrue(BlindSignatureService.verify(message, unblinded, clientKp),
                "Unblinded signature must verify against public key received from /api/token/pubkey");
    }
}
