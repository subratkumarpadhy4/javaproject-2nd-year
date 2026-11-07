package com.verivote.service.checkpoint;

import com.verivote.service.checkpoint.sink.FileSink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileSinkTest {

    @TempDir
    Path tempDir;

    private CheckpointService.Checkpoint sampleCheckpoint(long treeSize) {
        return new CheckpointService.Checkpoint(
                treeSize,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
                "c2lnbmF0dXJl",
                1700000000L
        );
    }

    @Test
    @DisplayName("1. write checkpoint -> file created at expected path")
    void writeCreatesFile() {
        FileSink sink = new FileSink(tempDir);
        CheckpointService.Checkpoint ckpt = sampleCheckpoint(5L);

        sink.write(ckpt);

        Path expectedCkptFile = tempDir.resolve("ckpt-5.json");
        assertTrue(Files.exists(expectedCkptFile), "ckpt-5.json must exist");
        assertTrue(Files.exists(tempDir.resolve("sink.ndjson")), "sink.ndjson must exist");
    }

    @Test
    @DisplayName("2. sink.ndjson has exactly 1 line")
    void sinkNdjsonSingleLine() throws IOException {
        FileSink sink = new FileSink(tempDir);
        CheckpointService.Checkpoint ckpt = sampleCheckpoint(1L);

        sink.write(ckpt);

        Path ndjsonFile = tempDir.resolve("sink.ndjson");
        List<String> lines = Files.readAllLines(ndjsonFile);
        assertEquals(1, lines.size(), "sink.ndjson must contain exactly 1 line");
    }

    @Test
    @DisplayName("3. writing same treeSize twice -> second write appends another line")
    void writeTwiceAppendsLine() throws IOException {
        FileSink sink = new FileSink(tempDir);
        CheckpointService.Checkpoint ckpt = sampleCheckpoint(2L);

        sink.write(ckpt);
        sink.write(ckpt);

        Path ndjsonFile = tempDir.resolve("sink.ndjson");
        List<String> lines = Files.readAllLines(ndjsonFile);
        assertEquals(2, lines.size(), "sink.ndjson must contain exactly 2 lines after two writes");
    }

    @Test
    @DisplayName("4. JSON structure parses back to same Checkpoint")
    void jsonParsesBack() {
        CheckpointService.Checkpoint ckpt = sampleCheckpoint(10L);
        String json = FileSink.formatJson(ckpt);

        CheckpointService.Checkpoint parsed = FileSink.parseJson(json);
        assertEquals(ckpt.treeSize(), parsed.treeSize());
        assertEquals(ckpt.merkleRoot(), parsed.merkleRoot());
        assertEquals(ckpt.headHash(), parsed.headHash());
        assertEquals(ckpt.signature(), parsed.signature());
        assertEquals(ckpt.createdAtSec(), parsed.createdAtSec());

        String ndjson = FileSink.formatNdjson(ckpt);
        CheckpointService.Checkpoint parsedNdjson = FileSink.parseJson(ndjson);
        assertEquals(ckpt, parsedNdjson);
    }
}
