package com.verivote.service.dto;

public record VoteResponse(
        String receiptHash,
        long blockHeight,
        String blockHash
) {}
