package com.verivote.data;

import com.verivote.data.entity.RecoveryEvent;
import com.verivote.data.entity.Vote;
import com.verivote.data.repo.RecoveryEventRepo;
import com.verivote.data.repo.VoteRepo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class JpaRecoveryData implements RecoveryData {

    private final VoteRepo voteRepo;
    private final RecoveryEventRepo recoveryEventRepo;

    public JpaRecoveryData(VoteRepo voteRepo, RecoveryEventRepo recoveryEventRepo) {
        this.voteRepo = voteRepo;
        this.recoveryEventRepo = recoveryEventRepo;
    }

    @Override
    @Transactional
    public long insertVote(long electionId, String tokenHash, String receiptHash,
                           String ciphertextB64, long blockHeight, String blockHash) {
        Vote vote = new Vote(electionId, tokenHash, receiptHash, ciphertextB64, blockHeight, blockHash);
        Vote saved = voteRepo.save(vote);
        return saved.getId();
    }

    @Override
    @Transactional
    public synchronized long insertEvent(EventRow event) {
        if (eventAt(event.blockHeight()).isPresent()) {
            throw new DuplicateEventException("Recovery event already exists at height " + event.blockHeight());
        }
        RecoveryEvent.Action action = RecoveryEvent.Action.valueOf(event.action());
        RecoveryEvent re = new RecoveryEvent(
                event.blockHeight(),
                action,
                event.blockHash(),
                event.tokenHash(),
                event.reason(),
                event.operator1(),
                event.signature1(),
                event.operator2(),
                event.signature2()
        );
        try {
            RecoveryEvent saved = recoveryEventRepo.saveAndFlush(re);
            return saved.getId();
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEventException("Recovery event already exists at height " + event.blockHeight());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventRow> eventAt(long blockHeight) {
        return recoveryEventRepo.findByBlockHeight(blockHeight)
                .map(e -> new EventRow(
                        e.getId(),
                        e.getBlockHeight(),
                        e.getAction().name(),
                        e.getBlockHash(),
                        e.getTokenHash(),
                        e.getReason(),
                        e.getOperator1(),
                        e.getSignature1(),
                        e.getOperator2(),
                        e.getSignature2()
                ));
    }
}
