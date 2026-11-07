package com.verivote.web.dto;

import java.util.List;

public record VerifyResponse(
        String status,
        Long index,
        Long treeSize,
        List<String> proof,
        String leafData
) {}
