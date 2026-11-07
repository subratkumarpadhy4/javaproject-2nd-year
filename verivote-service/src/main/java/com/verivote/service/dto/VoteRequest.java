package com.verivote.service.dto;

public record VoteRequest(
        byte[] ciphertext,
        byte[] tokenPlain,
        byte[] signature,
        byte[] clientNonce
) {}
