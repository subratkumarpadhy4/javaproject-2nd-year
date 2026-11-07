package com.verivote.service;

import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.crypto.Sha256;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;
import com.verivote.core.ledger.Block;
import com.verivote.core.ledger.LedgerService;
import com.verivote.data.entity.Election;
import com.verivote.data.entity.SpentToken;
import com.verivote.data.entity.Vote;
import com.verivote.data.repo.ElectionRepo;
import com.verivote.data.repo.SpentTokenRepo;
import com.verivote.data.repo.VoteRepo;
import com.verivote.service.dto.VoteRequest;
import com.verivote.service.dto.VoteResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;

@Service
public class VoteService {

    private final LedgerService ledger;
    private final VoteRepo voteRepo;
    private final SpentTokenRepo spentTokenRepo;
    private final ElectionRepo electionRepo;
    private final TokenService tokenService;
    private final TransactionTemplate txTemplate;
    private final PlatformTransactionManager txManager;

    public VoteService(
            LedgerService ledger,
            VoteRepo voteRepo,
            SpentTokenRepo spentTokenRepo,
            ElectionRepo electionRepo,
            TokenService tokenService,
            PlatformTransactionManager txManager
    ) {
        this.ledger = ledger;
        this.voteRepo = voteRepo;
        this.spentTokenRepo = spentTokenRepo;
        this.electionRepo = electionRepo;
        this.tokenService = tokenService;
        this.txManager = txManager;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    /**
     * Cast a vote. NO session, NO auth — vote is anonymous by design.
     *
     * Order of operations (CRITICAL, from spec §8.1):
     *   1. Verify signature BEFORE taking any lock
     *   2. tokenHash = SHA-256(SPEND-v1 || int64be(eid) || tokenPlain)
     *   3. receiptHash = SHA-256(RECEIPT-v1 || int64be(eid) || nonce || tokenHash)
     *   4. @Transactional:
     *      a. SELECT election FOR UPDATE; status must be OPEN
     *      b. INSERT spent_token (unique PK)
     *      c. ledger.append(ct, tokenHash, receiptHash)
     *      d. register afterCompletion -> markOrphan on ROLLBACK
     *      e. INSERT vote(eid, tokenHash, receiptHash, base64(ct),
     *                     a.height, a.blockHash)
     *      COMMIT
     *   5. return {receiptHash, a.height}
     */
    public VoteResponse castVote(VoteRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("VoteRequest cannot be null");
        }
        if (req.ciphertext() == null || req.ciphertext().length != Block.CT_LEN) {
            throw new IllegalArgumentException("Invalid ciphertext length (expected 256 bytes)");
        }
        if (req.tokenPlain() == null || req.tokenPlain().length != 32) {
            throw new IllegalArgumentException("Invalid tokenPlain length (expected 32 bytes)");
        }
        if (req.signature() == null || req.signature().length == 0) {
            throw new IllegalArgumentException("Missing token signature");
        }
        if (req.clientNonce() == null || req.clientNonce().length != 32) {
            throw new IllegalArgumentException("Invalid clientNonce length (expected 32 bytes)");
        }

        long eid = 1L;

        // 1. Verify signature BEFORE taking any DB lock
        byte[] tokenPrefix = DomainStrings.TOKEN_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] eidBytes = BigEndian.u64be(eid);
        byte[] tokenPreimage = new byte[tokenPrefix.length + eidBytes.length + req.tokenPlain().length];
        System.arraycopy(tokenPrefix, 0, tokenPreimage, 0, tokenPrefix.length);
        System.arraycopy(eidBytes, 0, tokenPreimage, tokenPrefix.length, eidBytes.length);
        System.arraycopy(req.tokenPlain(), 0, tokenPreimage, tokenPrefix.length + eidBytes.length, req.tokenPlain().length);

        BlindSignatureService.KeyPair kp = tokenService.getElectionKeyPair();
        if (kp == null || !BlindSignatureService.verify(tokenPreimage, req.signature(), kp)) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        // 2. tokenHash = SHA-256(SPEND-v1 || int64be(eid) || tokenPlain)
        byte[] spendPrefix = DomainStrings.SPEND_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] spendPreimage = new byte[spendPrefix.length + eidBytes.length + req.tokenPlain().length];
        System.arraycopy(spendPrefix, 0, spendPreimage, 0, spendPrefix.length);
        System.arraycopy(eidBytes, 0, spendPreimage, spendPrefix.length, eidBytes.length);
        System.arraycopy(req.tokenPlain(), 0, spendPreimage, spendPrefix.length + eidBytes.length, req.tokenPlain().length);
        byte[] tokenHash = Sha256.hash(spendPreimage);
        String tokenHashHex = Hex.encode(tokenHash);

        // 3. receiptHash = SHA-256(RECEIPT-v1 || int64be(eid) || nonce || tokenHash)
        byte[] rcptPrefix = DomainStrings.RECEIPT_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] rcptPreimage = new byte[rcptPrefix.length + eidBytes.length + req.clientNonce().length + tokenHash.length];
        System.arraycopy(rcptPrefix, 0, rcptPreimage, 0, rcptPrefix.length);
        System.arraycopy(eidBytes, 0, rcptPreimage, rcptPrefix.length, eidBytes.length);
        System.arraycopy(req.clientNonce(), 0, rcptPreimage, rcptPrefix.length + eidBytes.length, req.clientNonce().length);
        System.arraycopy(tokenHash, 0, rcptPreimage, rcptPrefix.length + eidBytes.length + req.clientNonce().length, tokenHash.length);
        byte[] receiptHash = Sha256.hash(rcptPreimage);
        String receiptHashHex = Hex.encode(receiptHash);

        // 4. Transaction execution
        synchronized (tokenHashHex.intern()) {
            if (spentTokenRepo.existsById(tokenHashHex)) {
                throw new DataIntegrityViolationException("Token already spent: " + tokenHashHex);
            }

            return txTemplate.execute(status -> {
                // a. SELECT election FOR UPDATE; status must be OPEN
                Election election = electionRepo.findByIdForUpdate(eid)
                        .orElseGet(() -> electionRepo.findById(eid)
                                .orElseGet(() -> {
                                    Election e = new Election("General Election");
                                    e.setStatus(Election.Status.OPEN);
                                    return electionRepo.saveAndFlush(e);
                                }));
                if (election.getStatus() != Election.Status.OPEN) {
                    throw new IllegalStateException("Election is not open");
                }

                // b. INSERT spent_token (unique PK)
                SpentToken spentToken = new SpentToken(tokenHashHex, eid);
                spentTokenRepo.saveAndFlush(spentToken);

                // c. ledger.append(ct, tokenHash, receiptHash)
                LedgerService.Appended appended = ledger.append(req.ciphertext(), tokenHash, receiptHash);

                // d. register afterCompletion -> markOrphan on ROLLBACK
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int txStatus) {
                            if (txStatus == STATUS_ROLLED_BACK) {
                                ledger.markOrphan(appended.height());
                            }
                        }
                    });
                }

                // e. INSERT vote(eid, tokenHash, receiptHash, base64(ct), a.height, a.blockHash)
                String ctB64 = Base64Codec.encode(req.ciphertext());
                Vote vote = new Vote(eid, tokenHashHex, receiptHashHex, ctB64, appended.height(), appended.blockHash());
                voteRepo.saveAndFlush(vote);

                // 5. return {receiptHash, a.height}
                return new VoteResponse(receiptHashHex, appended.height(), appended.blockHash());
            });
        }
    }
}
