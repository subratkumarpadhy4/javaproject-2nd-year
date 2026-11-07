package com.verivote.service.dto;

public record TotpRequest(String challengeToken, int code) {}
