package com.verivote.service.dto;

public record RegisterResponse(String status) {
    public RegisterResponse() {
        this("registered");
    }
}
