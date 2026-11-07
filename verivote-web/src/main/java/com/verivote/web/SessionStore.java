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
