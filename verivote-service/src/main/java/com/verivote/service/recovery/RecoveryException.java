package com.verivote.service.recovery;

public class RecoveryException extends RuntimeException {

    public enum Code {
        BAD_REQUEST,
        BLOCK_NOT_VOTE,
        BLOCK_MISSING,
        BLOCK_ALREADY_ORPHANED,
        SIGNATURE_INVALID,
        OPERATOR_UNKNOWN,
        OPERATOR_INACTIVE,
        OPERATOR_ROLE_MISMATCH,
        SAME_OPERATOR,
        PRECONDITION_FAILED,
        ALREADY_RECOVERED,
        ELECTION_NOT_CLOSED
    }

    private final Code code;

    public RecoveryException(Code code, String message) {
        super(code + ": " + message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
