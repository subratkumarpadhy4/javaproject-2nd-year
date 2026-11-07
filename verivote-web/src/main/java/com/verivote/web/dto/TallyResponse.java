package com.verivote.web.dto;

import com.verivote.service.tally.Ballot;

import java.util.List;
import java.util.Map;

public record TallyResponse(
        Map<Long, Long> counts,
        long invalidCount,
        long closeBlockHeight,
        String closeHeadHash,
        String chainMerkleRoot,
        List<Ballot> ballots,
        long tallyPublishedAtSec
) {}
