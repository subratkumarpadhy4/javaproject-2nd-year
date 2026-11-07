package com.verivote.service.audit;

public record Finding(
        Severity severity,
        FindingCode code,
        String message,
        Long blockHeight,
        Long treeSize,
        String remediation
) {}
