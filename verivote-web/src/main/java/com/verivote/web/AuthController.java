package com.verivote.web;

import com.verivote.data.entity.Voter;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.VoterService;
import com.verivote.service.dto.LoginResponse;
import com.verivote.service.dto.RegisterResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final VoterService voterService;
    private final VoterRepo voterRepo;
    private final SessionStore sessionStore;
    private final SecureRandom rnd = new SecureRandom();

    public AuthController(VoterService voterService, VoterRepo voterRepo, SessionStore sessionStore) {
        this.voterService = voterService;
        this.voterRepo = voterRepo;
        this.sessionStore = sessionStore;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> req) {
        // Matdaan Form Registration
        if (req.containsKey("name") && req.containsKey("pin")) {
            String name = req.get("name") != null ? req.get("name").toString().trim() : null;
            Object ageObj = req.get("age");
            String state = req.get("state") != null ? req.get("state").toString().trim() : "National";
            String pin = req.get("pin") != null ? req.get("pin").toString().trim() : "";

            if (name == null || name.isEmpty() || ageObj == null || pin.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Missing required fields"));
            }

            int age;
            try {
                age = Integer.parseInt(ageObj.toString().trim());
            } catch (NumberFormatException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid age format"));
            }

            if (age < 18) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Registration declined: You must be at least 18 years of age to register."));
            }

            if (age > 120) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Please enter a valid age between 18 and 120."));
            }

            if (name.length() < 2 || name.length() > 100) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Please enter your full name (2–100 characters)."));
            }

            if (!pin.matches("^\\d{4}$")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "PIN must be exactly 4 numeric digits."));
            }

            String voterId;
            do {
                int randomSix = 100000 + rnd.nextInt(900000);
                voterId = "VTR" + randomSix;
            } while (voterRepo.findByVoterId(voterId).isPresent() ||
                     voterRepo.findByNationalIdHmac(voterService.computeNationalIdHmac(voterId)).isPresent());

            try {
                voterService.register(voterId, name, pin, age, state);
                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                        "success", true,
                        "voterId", voterId,
                        "name", name,
                        "age", age,
                        "state", state
                ));
            } catch (IllegalStateException e) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
            }
        }

        // Standard / Test Suite RegisterRequest
        String nationalId = req.get("nationalId") != null ? req.get("nationalId").toString() : null;
        String displayName = req.get("displayName") != null ? req.get("displayName").toString() : "";
        String password = req.get("password") != null ? req.get("password").toString() : "";

        try {
            voterService.register(nationalId, displayName, password);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new RegisterResponse("registered"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> req) {
        // Matdaan Form Login
        if (req.containsKey("voterId") && req.containsKey("pin")) {
            String voterId = req.get("voterId") != null ? req.get("voterId").toString().trim() : "";
            String pin = req.get("pin") != null ? req.get("pin").toString().trim() : "";

            try {
                Long voterIdNum = voterService.verifyPassword(voterId, pin);
                Voter voter = voterService.findById(voterIdNum);
                String sessionToken = sessionStore.createSession(voterIdNum);

                Map<String, Object> body = profile(voter, sessionToken);
                if (voter.getVoterId() == null) {
                    body.put("voterId", voterId);
                }
                return ResponseEntity.ok(body);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Voter ID not found in electoral roll or incorrect PIN."));
            }
        }

        // Standard / Test Suite LoginRequest
        String nationalId = req.get("nationalId") != null ? req.get("nationalId").toString() : "";
        String password = req.get("password") != null ? req.get("password").toString() : "";

        try {
            Long voterId = voterService.verifyPassword(nationalId, password);
            String sessionToken = sessionStore.createSession(voterId);
            return ResponseEntity.ok(new LoginResponse(sessionToken));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /** Current voter profile for a session token (used to restore the portal after a reload). */
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Optional<Long> pk = sessionStore.getVoterId(SessionStore.bearer(authHeader));
        Optional<Voter> voter = pk.flatMap(voterRepo::findById);
        if (voter.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Session expired. Please sign in again."));
        }
        return ResponseEntity.ok(profile(voter.get(), null));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        sessionStore.invalidate(SessionStore.bearer(authHeader));
        return ResponseEntity.ok(Map.of("success", true));
    }

    private static Map<String, Object> profile(Voter v, String sessionToken) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", true);
        m.put("voterId", v.getVoterId());
        m.put("name", v.getDisplayName());
        m.put("hasVoted", v.isHasVoted());
        m.put("state", v.getState() != null ? v.getState() : "—");
        m.put("age", v.getAge());
        if (sessionToken != null) {
            m.put("sessionToken", sessionToken);
        }
        return m;
    }
}
