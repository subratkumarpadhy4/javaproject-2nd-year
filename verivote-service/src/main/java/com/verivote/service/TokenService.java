package com.verivote.service;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.data.entity.IssuanceCounter;
import com.verivote.data.entity.Voter;
import com.verivote.data.repo.IssuanceCounterRepo;
import com.verivote.data.repo.VoterRepo;
import com.verivote.service.dto.TokenIssuanceResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
public class TokenService {

    private final VoterRepo voterRepo;
    private final IssuanceCounterRepo counterRepo;
    private BlindSignatureService.KeyPair electionKeyPair;
    private final SecureRandom rnd = new SecureRandom();

    public TokenService(VoterRepo voterRepo, IssuanceCounterRepo counterRepo) {
        this.voterRepo = voterRepo;
        this.counterRepo = counterRepo;
    }

    @PostConstruct
    public void init() {
        if (electionKeyPair == null) {
            electionKeyPair = BlindSignatureService.generateKeyPair();
        }
    }

    @Transactional
    public TokenIssuanceResponse issueToken(Long voterId, byte[] blindedMessage) {
        // 1. Pessimistic lock on voter
        Voter voter = voterRepo.findByForUpdate(voterId).orElseThrow(
                () -> new IllegalArgumentException("voter not found"));
        if (voter.isHasVoted()) {
            throw new IllegalStateException("already voted");
        }
        voter.setHasVoted(true);
        voterRepo.save(voter);
        voterRepo.flush();   // ensure DB update happens before signing

        // 2. Increment IssuanceCounter
        IssuanceCounter counter = counterRepo.findByElectionId(1L)
                .orElseGet(() -> new IssuanceCounter(1L, 0L, 0L));
        counter.setIssued(counter.getIssued() + 1);
        counterRepo.save(counter);

        // 3. Sign blinded message
        byte[] sig = BlindSignatureService.sign(blindedMessage, electionKeyPair);

        // 4. Return response
        return new TokenIssuanceResponse(
                Base64Codec.encode(sig),
                counter.getIssued()
        );
    }

    public byte[] getElectionPublicKeySpki() {
        return electionKeyPair.publicKeySpki();
    }

    public BlindSignatureService.KeyPair getElectionKeyPair() {
        return electionKeyPair;
    }

    public void setElectionKeyPair(BlindSignatureService.KeyPair keyPair) {
        this.electionKeyPair = keyPair;
    }
}
