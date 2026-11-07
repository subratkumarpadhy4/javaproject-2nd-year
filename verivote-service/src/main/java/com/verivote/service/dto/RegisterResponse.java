package com.verivote.service.dto;

public record RegisterResponse(String totpSecretBase32, String otpauthUri) {}
