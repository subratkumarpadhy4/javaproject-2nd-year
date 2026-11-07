package com.verivote.web;

import com.verivote.service.TotpService;
import com.verivote.service.dto.LoginRequest;
import com.verivote.service.dto.LoginResponse;
import com.verivote.service.dto.RegisterRequest;
import com.verivote.service.dto.RegisterResponse;
import com.verivote.service.dto.TotpRequest;
import com.verivote.service.dto.TotpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SessionStore sessionStore;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    @DisplayName("1. POST /api/register -> 201")
    void registerSuccess() {
        RegisterRequest req = new RegisterRequest("AUTH-NAT-1", "Voter One", "Pass1234!");
        ResponseEntity<RegisterResponse> resp = restTemplate.postForEntity("/api/register", req, RegisterResponse.class);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().totpSecretBase32());
        assertTrue(resp.getBody().otpauthUri().contains("AUTH-NAT-1"));
    }

    @Test
    @DisplayName("2. POST /api/register same id -> 409")
    void registerDuplicate() {
        RegisterRequest req1 = new RegisterRequest("AUTH-NAT-2", "Voter Two", "Pass1234!");
        ResponseEntity<RegisterResponse> resp1 = restTemplate.postForEntity("/api/register", req1, RegisterResponse.class);
        assertEquals(HttpStatus.CREATED, resp1.getStatusCode());

        RegisterRequest req2 = new RegisterRequest("AUTH-NAT-2", "Voter Two Dup", "Pass1234!");
        ResponseEntity<String> resp2 = restTemplate.postForEntity("/api/register", req2, String.class);
        assertEquals(HttpStatus.CONFLICT, resp2.getStatusCode());
    }

    @Test
    @DisplayName("3. POST /api/login correct -> 200 with challengeToken")
    void loginCorrect() {
        restTemplate.postForEntity("/api/register", new RegisterRequest("AUTH-NAT-3", "Voter Three", "Pass1234!"), RegisterResponse.class);

        LoginRequest req = new LoginRequest("AUTH-NAT-3", "Pass1234!");
        ResponseEntity<LoginResponse> resp = restTemplate.postForEntity("/api/login", req, LoginResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().challengeToken());
    }

    @Test
    @DisplayName("4. POST /api/login wrong -> 401")
    void loginWrong() {
        restTemplate.postForEntity("/api/register", new RegisterRequest("AUTH-NAT-4", "Voter Four", "Pass1234!"), RegisterResponse.class);

        LoginRequest wrongPass = new LoginRequest("AUTH-NAT-4", "WrongPass!");
        ResponseEntity<String> respWrongPass = restTemplate.postForEntity("/api/login", wrongPass, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, respWrongPass.getStatusCode());

        LoginRequest unknownUser = new LoginRequest("AUTH-UNKNOWN-99", "Pass1234!");
        ResponseEntity<String> respUnknown = restTemplate.postForEntity("/api/login", unknownUser, String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, respUnknown.getStatusCode());
    }

    @Test
    @DisplayName("5. POST /api/login/totp correct code -> 200 + sessionToken")
    void loginTotpCorrect() {
        RegisterResponse reg = restTemplate.postForEntity("/api/register",
                new RegisterRequest("AUTH-NAT-5", "Voter Five", "Pass1234!"), RegisterResponse.class).getBody();
        assertNotNull(reg);

        LoginResponse login = restTemplate.postForEntity("/api/login",
                new LoginRequest("AUTH-NAT-5", "Pass1234!"), LoginResponse.class).getBody();
        assertNotNull(login);

        byte[] secret = TotpService.base32Decode(reg.totpSecretBase32());
        int code = TotpService.generateTotp(secret);

        TotpRequest totpReq = new TotpRequest(login.challengeToken(), code);
        ResponseEntity<TotpResponse> resp = restTemplate.postForEntity("/api/login/totp", totpReq, TotpResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().sessionToken());
    }

    @Test
    @DisplayName("6. POST /api/login/totp wrong code -> 401")
    void loginTotpWrongCode() {
        RegisterResponse reg = restTemplate.postForEntity("/api/register",
                new RegisterRequest("AUTH-NAT-6", "Voter Six", "Pass1234!"), RegisterResponse.class).getBody();
        assertNotNull(reg);

        LoginResponse login = restTemplate.postForEntity("/api/login",
                new LoginRequest("AUTH-NAT-6", "Pass1234!"), LoginResponse.class).getBody();
        assertNotNull(login);

        byte[] secret = TotpService.base32Decode(reg.totpSecretBase32());
        int code = TotpService.generateTotp(secret);
        int wrongCode = (code + 500_000) % 1_000_000;

        TotpRequest totpReq = new TotpRequest(login.challengeToken(), wrongCode);
        ResponseEntity<String> resp = restTemplate.postForEntity("/api/login/totp", totpReq, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    @DisplayName("7. Full chain: register -> login -> totp -> sessionToken valid in SessionStore")
    void fullAuthChain() {
        RegisterResponse reg = restTemplate.postForEntity("/api/register",
                new RegisterRequest("AUTH-NAT-7", "Voter Seven", "Pass1234!"), RegisterResponse.class).getBody();
        assertNotNull(reg);

        LoginResponse login = restTemplate.postForEntity("/api/login",
                new LoginRequest("AUTH-NAT-7", "Pass1234!"), LoginResponse.class).getBody();
        assertNotNull(login);

        byte[] secret = TotpService.base32Decode(reg.totpSecretBase32());
        int code = TotpService.generateTotp(secret);

        TotpResponse totpResp = restTemplate.postForEntity("/api/login/totp",
                new TotpRequest(login.challengeToken(), code), TotpResponse.class).getBody();
        assertNotNull(totpResp);

        assertTrue(sessionStore.getVoterId(totpResp.sessionToken()).isPresent(),
                "Session token must resolve to a valid voterId in SessionStore");
    }
}
