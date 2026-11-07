package com.verivote.web;

import com.verivote.service.VoterService;
import com.verivote.service.dto.LoginRequest;
import com.verivote.service.dto.LoginResponse;
import com.verivote.service.dto.RegisterRequest;
import com.verivote.service.dto.RegisterResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final VoterService voterService;
    private final SessionStore sessionStore;

    public AuthController(VoterService voterService, SessionStore sessionStore) {
        this.voterService = voterService;
        this.sessionStore = sessionStore;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        try {
            voterService.register(req.nationalId(), req.displayName(), req.password());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new RegisterResponse("registered"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            Long voterId = voterService.verifyPassword(req.nationalId(), req.password());
            String sessionToken = sessionStore.createSession(voterId);
            return ResponseEntity.ok(new LoginResponse(sessionToken));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
