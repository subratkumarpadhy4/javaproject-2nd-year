package com.verivote.web;

import com.verivote.core.crypto.Shamir;
import com.verivote.service.tally.TallyResult;
import com.verivote.service.tally.TallyService;
import com.verivote.web.dto.TallyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class TallyController {

    private final TallyService tallyService;

    @Autowired
    public TallyController(TallyService tallyService) {
        this.tallyService = tallyService;
    }

    @PostMapping("/tally")
    public ResponseEntity<TallyResponse> tally(
            @RequestParam(name = "electionId", defaultValue = "1") long electionId,
            @RequestBody List<Shamir.Share> shares
    ) {
        TallyResult res = tallyService.tally(electionId, shares);
        return ResponseEntity.ok(new TallyResponse(
                res.counts(),
                res.invalidCount(),
                res.closeBlockHeight(),
                res.closeHeadHash(),
                res.chainMerkleRoot(),
                res.ballots(),
                res.tallyPublishedAtSec()
        ));
    }
}
