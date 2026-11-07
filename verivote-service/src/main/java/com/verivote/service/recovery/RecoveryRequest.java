package com.verivote.service.recovery;

public record RecoveryRequest(
        long blockHeight,
        Action action,
        String reason,
        String blockHash,
        String tokenHash,
        String operator1,
        byte[] signature1,
        String operator2,
        byte[] signature2
) {
    public enum Action {
        ACCEPT_AS_ORPHAN,
        RECOVER_FROM_LEDGER
    }

    public RecoveryRequest(
            long blockHeight,
            Action action,
            String reason,
            String operator1,
            byte[] signature1,
            String operator2,
            byte[] signature2
    ) {
        this(blockHeight, action, reason, null, null, operator1, signature1, operator2, signature2);
    }
}
