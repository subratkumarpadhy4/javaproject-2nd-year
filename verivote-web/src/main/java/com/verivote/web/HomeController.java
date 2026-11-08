package com.verivote.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> home() {
        return ResponseEntity.ok(Map.of(
                "system", "VeriVote-J Cryptographically Verifiable E-Voting Service",
                "status", "ONLINE",
                "version", "0.1.0-SNAPSHOT",
                "timestamp", Instant.now().toString(),
                "endpoints", Map.of(
                        "pubkey", "/api/token/pubkey",
                        "receipts", "/bb/receipts",
                        "checkpoints", "/bb/checkpoints"
                )
        ));
    }
}
