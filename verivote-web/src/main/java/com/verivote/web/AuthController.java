package com.verivote.web;

import com.verivote.service.TotpService;
import com.verivote.service.VoterService;
import com.verivote.service.dto.LoginRequest;
import com.verivote.service.dto.LoginResponse;
import com.verivote.service.dto.RegisterRequest;
import com.verivote.service.dto.RegisterResponse;
import com.verivote.service.dto.TotpRequest;
import com.verivote.service.dto.TotpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

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
            byte[] secretBytes = voterService.register(req.nationalId(), req.displayName(), req.password());
            String totpSecretBase32 = TotpService.base32Encode(secretBytes);
            String otpauthUri = "otpauth://totp/VeriVote:" + req.nationalId()
                    + "?secret=" + totpSecretBase32 + "&issuer=VeriVote-J";
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new RegisterResponse(totpSecretBase32, otpauthUri));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        try {
            Long voterId = voterService.verifyPassword(req.nationalId(), req.password());
            String challengeToken = sessionStore.createChallenge(voterId);
            return ResponseEntity.ok(new LoginResponse(challengeToken));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login/totp")
    public ResponseEntity<?> loginTotp(@RequestBody TotpRequest req) {
        Optional<Long> voterIdOpt = sessionStore.consumeChallenge(req.challengeToken());
        if (voterIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid or expired challenge token"));
        }
        Long voterId = voterIdOpt.get();
        try {
            voterService.verifyTotp(voterId, req.code());
            String sessionToken = sessionStore.createSession(voterId);
            return ResponseEntity.ok(new TotpResponse(sessionToken));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
