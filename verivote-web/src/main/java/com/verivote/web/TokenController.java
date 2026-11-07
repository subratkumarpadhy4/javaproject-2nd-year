package com.verivote.web;

import com.verivote.core.encoding.Base64Codec;
import com.verivote.service.TokenService;
import com.verivote.service.dto.TokenIssuanceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/token")
public class TokenController {

    private final TokenService tokenService;
    private final SessionStore sessionStore;

    public TokenController(TokenService tokenService, SessionStore sessionStore) {
        this.tokenService = tokenService;
        this.sessionStore = sessionStore;
    }

    @GetMapping("/pubkey")
    public ResponseEntity<Map<String, String>> pubkey() {
        String spkiB64 = Base64Codec.encode(tokenService.getElectionPublicKeySpki());
        Map<String, String> resp = new HashMap<>();
        resp.put("pubkey", spkiB64);
        resp.put("publicKeySpki", spkiB64);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/request")
    public ResponseEntity<?> request(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String sessionToken = null;
        if (auth != null && auth.toLowerCase().startsWith("bearer ")) {
            sessionToken = auth.substring(7).trim();
        } else if (body != null && body.containsKey("sessionToken")) {
            sessionToken = body.get("sessionToken");
        }

        if (sessionToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "missing session token"));
        }

        Optional<Long> voterIdOpt = sessionStore.getVoterId(sessionToken);
        if (voterIdOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid or expired session token"));
        }

        if (body == null || !body.containsKey("blindedMessage")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "missing blindedMessage"));
        }

        byte[] blindedMessage = Base64Codec.decode(body.get("blindedMessage"));
        try {
            TokenIssuanceResponse response = tokenService.issueToken(voterIdOpt.get(), blindedMessage);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
