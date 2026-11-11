package com.verivote.web;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    public record ChallengeEntry(Long voterId, Instant expiresAt) {}

    private final Map<String, Long> sessionToVoter = new ConcurrentHashMap<>();
    private final Map<String, ChallengeEntry> challenges = new ConcurrentHashMap<>();

    public String createSession(Long voterId) {
        String token = UUID.randomUUID().toString();
        sessionToVoter.put(token, voterId);
        return token;
    }

    public Optional<Long> getVoterId(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionToVoter.get(token));
    }

    public void invalidate(String token) {
        if (token != null) {
            sessionToVoter.remove(token);
        }
    }

    // ---- Election-officer (admin) sessions ----
    private static final long ADMIN_SESSION_HOURS = 8;
    private final Map<String, Instant> adminSessions = new ConcurrentHashMap<>();

    public String createAdminSession() {
        String token = UUID.randomUUID().toString();
        adminSessions.put(token, Instant.now().plus(ADMIN_SESSION_HOURS, ChronoUnit.HOURS));
        return token;
    }

    public boolean isAdminSession(String token) {
        if (token == null) {
            return false;
        }
        Instant expiry = adminSessions.get(token);
        if (expiry == null) {
            return false;
        }
        if (Instant.now().isAfter(expiry)) {
            adminSessions.remove(token);
            return false;
        }
        return true;
    }

    public void invalidateAdmin(String token) {
        if (token != null) {
            adminSessions.remove(token);
        }
    }

    /** Extracts the token from an {@code Authorization: Bearer <token>} header value. */
    public static String bearer(String authHeader) {
        if (authHeader == null) {
            return null;
        }
        String h = authHeader.trim();
        if (h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            h = h.substring(7).trim();
        }
        return h.isEmpty() ? null : h;
    }

    /** Drops every voter session (used by the demo reset). */
    public void clearVoterSessions() {
        sessionToVoter.clear();
    }

    public String createChallenge(Long voterId) {
        String token = UUID.randomUUID().toString();
        challenges.put(token, new ChallengeEntry(voterId, Instant.now().plus(5, ChronoUnit.MINUTES)));
        return token;
    }

    public Optional<Long> consumeChallenge(String token) {
        if (token == null) {
            return Optional.empty();
        }
        ChallengeEntry entry = challenges.remove(token);
        if (entry != null && Instant.now().isBefore(entry.expiresAt())) {
            return Optional.of(entry.voterId());
        }
        return Optional.empty();
    }
}
