package com.verivote.service.recovery;

public record RecoveryResult(long eventId, long blockHeight, RecoveryRequest.Action action) {}
