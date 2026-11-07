package com.verivote.service.checkpoint.sink;

import com.verivote.service.checkpoint.CheckpointService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class GitSink implements ExternalSink {

    private final Path repoDir;
    private final FileSink fileSink;

    public GitSink(Path repoDir) {
        if (repoDir == null || !Files.isDirectory(repoDir) || !Files.isDirectory(repoDir.resolve(".git"))) {
            throw new IllegalArgumentException("Git repository does not exist or is not initialized: " + repoDir);
        }
        this.repoDir = repoDir.toAbsolutePath().normalize();
        this.fileSink = new FileSink(this.repoDir);
    }

    public GitSink(String repoDir) {
        this(Paths.get(repoDir));
    }

    public Path getRepoDir() {
        return repoDir;
    }

    @Override
    public void write(CheckpointService.Checkpoint ckpt) throws SinkException {
        if (ckpt == null) {
            throw new IllegalArgumentException("Checkpoint cannot be null");
        }
        // 1. Write the files inside the git repo
        fileSink.write(ckpt);

        String ckptFileName = "ckpt-" + ckpt.treeSize() + ".json";
        String commitMsg = "ckpt " + ckpt.treeSize() + " " + ckpt.merkleRoot();

        // 2. git -C <repo> add sink.ndjson ckpt-<n>.json
        runGitCommand(List.of("git", "-C", repoDir.toString(), "add", "sink.ndjson", ckptFileName));

        // 3. git -C <repo> -c commit.gpgsign=false -c user.email=verivote@local -c user.name=verivote commit -m ...
        runGitCommand(List.of(
                "git", "-C", repoDir.toString(),
                "-c", "commit.gpgsign=false",
                "-c", "user.email=verivote@local",
                "-c", "user.name=verivote",
                "commit", "-m", commitMsg
        ));
    }

    private void runGitCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new SinkException("Git command failed (exit " + exitCode + "): " + output);
            }
        } catch (IOException e) {
            throw new SinkException("Git command failed with IOException: " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SinkException("Git command interrupted: " + command, e);
        }
    }

    @Override
    public String name() {
        return "GitSink";
    }
}
