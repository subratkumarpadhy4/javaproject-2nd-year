package com.verivote.service.tally;

import java.util.List;
import java.util.Map;

public record TallyResult(
        Map<Long, Long> counts,
        long invalidCount,
        long closeBlockHeight,
        String closeHeadHash,
        String chainMerkleRoot,
        List<Ballot> ballots,
        long tallyPublishedAtSec
) {}
