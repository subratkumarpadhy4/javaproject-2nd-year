package com.verivote.service.audit;

import com.verivote.service.checkpoint.CheckpointService;
import com.verivote.service.checkpoint.sink.FileSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class CheckpointReader {

    private static final Logger log = LoggerFactory.getLogger(CheckpointReader.class);

    private final Path fileDir;
    private final Path gitDir;

    public CheckpointReader(Path fileDir, Path gitDir) {
        this.fileDir = fileDir;
        this.gitDir = gitDir;
    }

    public CheckpointReader(Path fileDir) {
        this(fileDir, null);
    }

    public CheckpointReader(String fileDir, String gitDir) {
        this(fileDir != null ? Paths.get(fileDir) : null, gitDir != null ? Paths.get(gitDir) : null);
    }

    public CheckpointReader(String fileDir) {
        this(fileDir != null ? Paths.get(fileDir) : null, null);
    }

    public List<CheckpointService.Checkpoint> loadAll() {
        List<CheckpointService.Checkpoint> result = new ArrayList<>();
        Set<Path> scannedDirs = new LinkedHashSet<>();
        if (fileDir != null && Files.isDirectory(fileDir)) {
            scannedDirs.add(fileDir);
        }
        if (gitDir != null && Files.isDirectory(gitDir)) {
            scannedDirs.add(gitDir);
        }

        for (Path dir : scannedDirs) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().startsWith("ckpt-") && p.getFileName().toString().endsWith(".json"))
                        .forEach(p -> {
                            try {
                                String content = Files.readString(p);
                                CheckpointService.Checkpoint ckpt = FileSink.parseJson(content);
                                result.add(ckpt);
                            } catch (Exception e) {
                                log.warn("Malformed checkpoint file ignored: {}", p, e);
                            }
                        });
            } catch (IOException e) {
                log.warn("Failed to list checkpoints in directory: {}", dir, e);
            }
        }

        result.sort(Comparator.comparingLong(CheckpointService.Checkpoint::treeSize));
        return result;
    }
}
