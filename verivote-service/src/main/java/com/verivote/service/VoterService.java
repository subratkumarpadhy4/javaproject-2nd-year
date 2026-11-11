package com.verivote.service;

import com.verivote.core.crypto.Hmac;
import com.verivote.core.encoding.Hex;
import com.verivote.data.entity.Voter;
import com.verivote.data.repo.VoterRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Optional;

@Service
public class VoterService {

    private final VoterRepo voterRepo;
    private final byte[] hmacKey;
    private final SecureRandom rnd = new SecureRandom();

    public VoterService(
            VoterRepo voterRepo,
            @Value("${verivote.crypto.hmac-master-key:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef}") String hmacKeyProp
    ) {
        this.voterRepo = voterRepo;
        byte[] keyBytes;
        try {
            keyBytes = Hex.decode(hmacKeyProp);
        } catch (Exception e) {
            keyBytes = hmacKeyProp.getBytes(StandardCharsets.UTF_8);
        }
        this.hmacKey = keyBytes;
    }

    /**
     * Register a new voter.
     *
     * @return the raw 20-byte TOTP secret
     * @throws IllegalStateException if national_id is already registered
     */
    @Transactional
    public byte[] register(String nationalId, String displayName, String password) {
        return register(nationalId, displayName, password, null, null);
    }

    @Transactional
    public byte[] register(String nationalId, String displayName, String password, Integer age, String state) {
        String hmac = computeNationalIdHmac(nationalId);
        if (voterRepo.findByNationalIdHmac(hmac).isPresent() || (nationalId != null && voterRepo.findByVoterId(nationalId).isPresent())) {
            throw new IllegalStateException("national id already registered");
        }

        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        byte[] totpSecret = new byte[20];
        rnd.nextBytes(totpSecret);

        Voter voter = new Voter(hmac, displayName, passwordHash, totpSecret);
        voter.setVoterId(nationalId);
        voter.setAge(age);
        voter.setState(state);
        try {
            voterRepo.saveAndFlush(voter);
            return totpSecret;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("national id already registered", e);
        }
    }

    /**
     * Verify password. Returns voter_id if valid, throws otherwise.
     * CRITICAL: same error message for "unknown voter" and "wrong password"
     * (prevents user enumeration).
     */
    @Transactional(readOnly = true)
    public Long verifyPassword(String nationalId, String password) {
        String hmac = computeNationalIdHmac(nationalId);
        Optional<Voter> voterOpt = voterRepo.findByNationalIdHmac(hmac);
        if (voterOpt.isEmpty()) {
            voterOpt = voterRepo.findByVoterId(nationalId);
        }
        if (voterOpt.isEmpty()) {
            throw new IllegalArgumentException("invalid credentials");
        }
        Voter voter = voterOpt.get();
        if (!BCrypt.checkpw(password, voter.getPasswordHash())) {
            throw new IllegalArgumentException("invalid credentials");
        }
        return voter.getId();
    }

    @Transactional(readOnly = true)
    public Voter findById(Long id) {
        return voterRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("voter not found"));
    }

    public String computeNationalIdHmac(String nationalId) {
        return Hex.encode(Hmac.sha256(hmacKey, nationalId.getBytes(StandardCharsets.UTF_8)));
    }
}
