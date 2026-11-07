package com.verivote.client.state;

public final class SessionState {

    private String sessionToken;
    private byte[] tokenPlain;
    private byte[] signature;
    private byte[] ballotId;
    private String receiptHash;
    private long blockHeight;

    public SessionState() {}

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public byte[] getTokenPlain() {
        return tokenPlain;
    }

    public void setTokenPlain(byte[] tokenPlain) {
        this.tokenPlain = tokenPlain;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getBallotId() {
        return ballotId;
    }

    public void setBallotId(byte[] ballotId) {
        this.ballotId = ballotId;
    }

    public String getReceiptHash() {
        return receiptHash;
    }

    public void setReceiptHash(String receiptHash) {
        this.receiptHash = receiptHash;
    }

    public long getBlockHeight() {
        return blockHeight;
    }

    public void setBlockHeight(long blockHeight) {
        this.blockHeight = blockHeight;
    }

    public void clear() {
        this.sessionToken = null;
        this.tokenPlain = null;
        this.signature = null;
        this.ballotId = null;
        this.receiptHash = null;
        this.blockHeight = 0;
    }
}
