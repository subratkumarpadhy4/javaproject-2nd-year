package com.verivote.web.dto;

public record CheckpointResponse(
        long treeSize,
        String merkleRoot,
        String headHash,
        String signature,
        Long createdAtSec
) {}
