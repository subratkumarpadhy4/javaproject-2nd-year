package com.verivote.web;

import com.verivote.data.entity.Candidate;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SampleBallotsTest {

    private static List<Candidate> options() {
        String[] codes = {"bjp", "tdp", "inc", "ysrcp", "jdu", "aap", "tmc", "bsp", "cpim", "nota"};
        List<Candidate> list = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) {
            Candidate c = new Candidate(1L, "Nominee", codes[i].toUpperCase(), "Demo", codes[i]);
            c.setId((long) (i + 1));
            list.add(c);
        }
        return list;
    }

    @Test
    void favouriteAlwaysStrictlyWinsAndSplitIsRandom() {
        List<Candidate> options = options();
        Candidate bjp = options.get(0);
        Random rnd = new Random(42);
        Set<Map<Long, Long>> distinctSplits = new HashSet<>();
        Set<Long> bjpCounts = new HashSet<>();

        for (int run = 0; run < 5000; run++) {
            List<Candidate> ballots = MatdaanService.sampleBallots(options, bjp, 14, rnd);
            assertEquals(14, ballots.size());
            Map<Long, Long> counts = ballots.stream()
                    .collect(Collectors.groupingBy(Candidate::getId, Collectors.counting()));
            long bjpVotes = counts.getOrDefault(bjp.getId(), 0L);
            counts.forEach((id, v) -> {
                if (!id.equals(bjp.getId())) {
                    assertTrue(v < bjpVotes, "BJP must lead strictly: " + counts);
                }
            });
            distinctSplits.add(counts);
            bjpCounts.add(bjpVotes);
        }
        assertTrue(distinctSplits.size() > 500, "splits should vary run to run");
        assertTrue(bjpCounts.size() > 1, "BJP's own count should vary");
    }

    @Test
    void worksForPartialBatches() {
        List<Candidate> options = options();
        Random rnd = new Random(7);
        for (int n = 1; n <= 14; n++) {
            for (int run = 0; run < 200; run++) {
                List<Candidate> ballots = MatdaanService.sampleBallots(options, options.get(0), n, rnd);
                assertEquals(n, ballots.size());
                Map<Long, Long> counts = ballots.stream()
                        .collect(Collectors.groupingBy(Candidate::getId, Collectors.counting()));
                long lead = counts.getOrDefault(1L, 0L);
                counts.forEach((id, v) -> { if (id != 1L) assertTrue(v < lead); });
            }
        }
        assertTrue(MatdaanService.sampleBallots(options, options.get(0), 0, rnd).isEmpty());
    }
}
