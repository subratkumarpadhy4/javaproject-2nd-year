package com.verivote.web;

import com.verivote.service.VoteService;
import com.verivote.service.dto.VoteRequest;
import com.verivote.service.dto.VoteResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @PostMapping("/vote")
    public ResponseEntity<VoteResponse> vote(@RequestBody VoteRequest req) {
        VoteResponse resp = voteService.castVote(req);
        return ResponseEntity.ok(resp);
    }
}
