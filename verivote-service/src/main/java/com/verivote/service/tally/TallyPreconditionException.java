package com.verivote.service.tally;

import com.verivote.service.audit.TamperReport;

public class TallyPreconditionException extends RuntimeException {

    private final TamperReport tamperReport;

    public TallyPreconditionException(TamperReport tamperReport) {
        super("Tally precondition failed: audit report is not clean");
        this.tamperReport = tamperReport;
    }

    public TamperReport getTamperReport() {
        return tamperReport;
    }
}
