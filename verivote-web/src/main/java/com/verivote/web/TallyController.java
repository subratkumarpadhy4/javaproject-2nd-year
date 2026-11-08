package com.verivote.web;

import com.verivote.core.crypto.Shamir;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.service.tally.TallyKeyService;
import com.verivote.service.tally.TallyResult;
import com.verivote.service.tally.TallyService;
import com.verivote.web.dto.TallyResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class TallyController {

    private final TallyService tallyService;
    private final TallyKeyService tallyKeyService;

    @Autowired
    public TallyController(TallyService tallyService, TallyKeyService tallyKeyService) {
        this.tallyService = tallyService;
        this.tallyKeyService = tallyKeyService;
    }

    @GetMapping("/tally/shares")
    public ResponseEntity<List<Map<String, Object>>> getDemoShares() {
        List<Shamir.Share> shares = tallyKeyService.getShares();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Shamir.Share s : shares) {
            String b64 = Base64Codec.encode(s.value());
            result.add(Map.of(
                    "index", s.index(),
                    "value", b64,
                    "shareString", s.index() + ":" + b64
            ));
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/tally")
    public ResponseEntity<TallyResponse> tally(
            @RequestParam(name = "electionId", defaultValue = "1") long electionId,
            @RequestBody List<Map<String, Object>> rawShares
    ) {
        List<Shamir.Share> shares = new ArrayList<>();
        for (int i = 0; i < rawShares.size(); i++) {
            Map<String, Object> map = rawShares.get(i);
            int idx = map.containsKey("index") ? ((Number) map.get("index")).intValue() : (i + 1);
            Object valObj = map.get("value");
            byte[] valBytes;
            if (valObj instanceof byte[] b) {
                valBytes = b;
            } else if (valObj instanceof String s) {
                if (s.contains(":")) {
                    String[] parts = s.split(":", 2);
                    idx = Integer.parseInt(parts[0].trim());
                    s = parts[1].trim();
                }
                valBytes = Base64Codec.decode(s);
            } else {
                throw new IllegalArgumentException("Invalid share value format");
            }
            shares.add(new Shamir.Share(idx, valBytes));
        }

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
