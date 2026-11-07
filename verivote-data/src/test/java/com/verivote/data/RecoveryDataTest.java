package com.verivote.data;

import com.verivote.data.RecoveryData.DuplicateEventException;
import com.verivote.data.RecoveryData.EventRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestApplication.class)
@TestPropertySource(locations = "classpath:application-data.properties")
class RecoveryDataTest {

    @Autowired
    private RecoveryData recoveryData;

    @Test
    @DisplayName("Fix F2 proof: single and duplicate event insertion")
    void testSingleAndDuplicateEvent() {
        EventRow row42 = new EventRow(
                0L, 42L, "ACCEPT_AS_ORPHAN", "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff",
                "112233445566778899aabbccddeeff00112233445566778899aabbccddeeff00", "Ledger repair orphan",
                "operator-alpha", "sig1-mock-base64", null, null
        );

        // 1. insertEvent(row at height=42) succeeds -> returns id
        long id1 = recoveryData.insertEvent(row42);
        assertTrue(id1 > 0);

        // 2. insertEvent(row at height=42 again) throws DuplicateEventException
        assertThrows(DuplicateEventException.class, () -> recoveryData.insertEvent(row42));

        // 3. eventAt(42) returns the first row
        Optional<EventRow> fetched42 = recoveryData.eventAt(42L);
        assertTrue(fetched42.isPresent());
        assertEquals(42L, fetched42.get().blockHeight());
        assertEquals("ACCEPT_AS_ORPHAN", fetched42.get().action());
        assertEquals("operator-alpha", fetched42.get().operator1());

        // 4. eventAt(43) returns Optional.empty()
        Optional<EventRow> fetched43 = recoveryData.eventAt(43L);
        assertTrue(fetched43.isEmpty());
    }

    @Test
    @DisplayName("Fix F2 proof: concurrent 10 threads inserting same height=99")
    void testConcurrentDuplicateInsertions() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    EventRow row = new EventRow(
                            0L, 99L, "RECOVER_FROM_LEDGER", "aabbccdd00112233445566778899aabbccddeeff00112233445566778899aabb",
                            "ddeeff00112233445566778899aabbccddeeff00112233445566778899aabbcc", "Recovery concurrent " + idx,
                            "operator-" + idx, "sig-" + idx, null, null
                    );
                    recoveryData.insertEvent(row);
                    successCount.incrementAndGet();
                } catch (DuplicateEventException e) {
                    duplicateCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one thread must succeed");
        assertEquals(9, duplicateCount.get(), "Exactly 9 threads must fail with DuplicateEventException");
    }
}
