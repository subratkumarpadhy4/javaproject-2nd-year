package com.verivote.service.checkpoint;

import com.verivote.service.checkpoint.sink.GitSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitSinkTest {

    @TempDir
    Path repoDir;

    private GitSink gitSink;

    private CheckpointService.Checkpoint sampleCheckpoint(long treeSize) {
        return new CheckpointService.Checkpoint(
                treeSize,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
                "c2lnbmF0dXJl",
                1700000000L
        );
    }

    private void runCommand(List<String> command) throws Exception {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        int code = p.waitFor();
        if (code != 0) {
            String out = new String(p.getInputStream().readAllBytes());
            throw new RuntimeException("Command failed (" + code + "): " + out);
        }
    }

    private int gitCommitCount() throws Exception {
        Process p = new ProcessBuilder("git", "-C", repoDir.toString(), "rev-list", "--count", "HEAD")
                .redirectErrorStream(true)
                .start();
        int code = p.waitFor();
        if (code != 0) {
            return 0;
        }
        String out = new String(p.getInputStream().readAllBytes()).trim();
        return Integer.parseInt(out);
    }

    @BeforeEach
    void setUp() throws Exception {
        runCommand(List.of("git", "-C", repoDir.toString(), "init"));
        runCommand(List.of("git", "-C", repoDir.toString(), "config", "user.email", "verivote@local"));
        runCommand(List.of("git", "-C", repoDir.toString(), "config", "user.name", "verivote"));
        gitSink = new GitSink(repoDir);
    }

    @Test
    @DisplayName("1. Init temp git repo -> constructor succeeds, invalid dir throws")
    void initAndConstructorCheck() {
        assertNotNull(gitSink);
        Path invalidDir = repoDir.resolve("non_existent_subdir");
        assertThrows(IllegalArgumentException.class, () -> new GitSink(invalidDir));
    }

    @Test
    @DisplayName("2. write checkpoint -> git log shows 1 commit, files present")
    void writeCheckpointCreatesCommit() throws Exception {
        CheckpointService.Checkpoint ckpt = sampleCheckpoint(1L);
        gitSink.write(ckpt);

        assertEquals(1, gitCommitCount(), "Git log must show exactly 1 commit");
        assertTrue(Files.exists(repoDir.resolve("ckpt-1.json")), "ckpt-1.json must exist in repo");
        assertTrue(Files.exists(repoDir.resolve("sink.ndjson")), "sink.ndjson must exist in repo");
    }

    @Test
    @DisplayName("3. write another -> 2 commits")
    void writeAnotherCreatesSecondCommit() throws Exception {
        gitSink.write(sampleCheckpoint(1L));
        gitSink.write(sampleCheckpoint(2L));

        assertEquals(2, gitCommitCount(), "Git log must show 2 commits");
        assertTrue(Files.exists(repoDir.resolve("ckpt-2.json")));
    }

    @Test
    @DisplayName("4. sink.ndjson accumulates lines")
    void sinkNdjsonAccumulatesLines() throws Exception {
        gitSink.write(sampleCheckpoint(1L));
        gitSink.write(sampleCheckpoint(2L));
        gitSink.write(sampleCheckpoint(3L));

        List<String> lines = Files.readAllLines(repoDir.resolve("sink.ndjson"));
        assertEquals(3, lines.size(), "sink.ndjson must accumulate 3 lines");
    }
}
