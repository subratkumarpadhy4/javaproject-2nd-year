package com.verivote.data;

import java.util.Optional;

public interface RecoveryData {

    record EventRow(
        long id, long blockHeight, String action, String blockHash,
        String tokenHash, String reason,
        String operator1, String signature1,
        String operator2, String signature2
    ) {}

    // Insert a vote row (RECOVER_FROM_LEDGER path). Returns the generated id.
    long insertVote(long electionId, String tokenHash, String receiptHash,
                    String ciphertextB64, long blockHeight, String blockHash);

    // Insert a recovery event. Throws DuplicateEventException if a row 
    // already exists for this blockHeight. Must be synchronized to prevent 
    // races (Fix F2).
    long insertEvent(EventRow event);

    // Fetch existing recovery event at this height (used by F1 event-first 
    // check). Empty if not found.
    Optional<EventRow> eventAt(long blockHeight);

    class DuplicateEventException extends RuntimeException {
        public DuplicateEventException(String message) { super(message); }
    }
}
