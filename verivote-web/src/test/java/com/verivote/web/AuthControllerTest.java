package com.verivote.web;

import com.verivote.service.dto.LoginRequest;
import com.verivote.service.dto.LoginResponse;
import com.verivote.service.dto.RegisterRequest;
import com.verivote.service.dto.RegisterResponse;
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
    @DisplayName("3. POST /api/login correct -> 200 with sessionToken")
    void loginCorrect() {
        restTemplate.postForEntity("/api/register", new RegisterRequest("AUTH-NAT-3", "Voter Three", "Pass1234!"), RegisterResponse.class);

        LoginRequest req = new LoginRequest("AUTH-NAT-3", "Pass1234!");
        ResponseEntity<LoginResponse> resp = restTemplate.postForEntity("/api/login", req, LoginResponse.class);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertNotNull(resp.getBody().sessionToken());
        assertTrue(sessionStore.getVoterId(resp.getBody().sessionToken()).isPresent(),
                "Session token must resolve to voterId in SessionStore");
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
}
