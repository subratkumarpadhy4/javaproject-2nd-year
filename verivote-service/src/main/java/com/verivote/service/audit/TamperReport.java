package com.verivote.service.audit;

import java.util.List;

public record TamperReport(
        boolean clean,
        long auditedAtSec,
        long ledgerBlockCount,
        long ledgerRealVoteCount,
        long ledgerOrphanCount,
        long dbVoteCount,
        List<Finding> findings
) {
    public boolean blocksTally() {
        return !clean;
    }
}
