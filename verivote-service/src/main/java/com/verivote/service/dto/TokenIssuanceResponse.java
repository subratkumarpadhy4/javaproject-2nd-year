package com.verivote.service.dto;

public record TokenIssuanceResponse(String signature, long issuanceCount) {
    public String signatureB64() {
        return signature;
    }
}
