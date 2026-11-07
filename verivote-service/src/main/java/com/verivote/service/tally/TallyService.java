package com.verivote.service.tally;

import com.verivote.core.crypto.Shamir;

import java.util.List;
import java.util.Optional;

public interface TallyService {

    TallyResult tally(long electionId, List<Shamir.Share> keyShares);

    Optional<TallyResult> getLatestTally(long electionId);
}
