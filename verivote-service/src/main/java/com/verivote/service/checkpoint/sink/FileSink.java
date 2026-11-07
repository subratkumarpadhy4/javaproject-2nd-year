package com.verivote.service.checkpoint.sink;

import com.verivote.service.checkpoint.CheckpointService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileSink implements ExternalSink {

    private final Path directory;

    public FileSink(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Directory cannot be null");
        }
        this.directory = directory;
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new SinkException("Failed to create sink directory: " + directory, e);
        }
    }

    public FileSink(String directory) {
        this(Paths.get(directory));
    }

    public Path getDirectory() {
        return directory;
    }

    @Override
    public void write(CheckpointService.Checkpoint ckpt) throws SinkException {
        if (ckpt == null) {
            throw new IllegalArgumentException("Checkpoint cannot be null");
        }
        try {
            Files.createDirectories(directory);

            Path ckptFile = directory.resolve("ckpt-" + ckpt.treeSize() + ".json");
            byte[] ckptBytes = formatJson(ckpt).getBytes(StandardCharsets.UTF_8);
            try (FileChannel ch = FileChannel.open(
                    ckptFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ch.write(ByteBuffer.wrap(ckptBytes));
                ch.force(true);
            }

            Path ndjsonFile = directory.resolve("sink.ndjson");
            byte[] ndjsonBytes = formatNdjson(ckpt).getBytes(StandardCharsets.UTF_8);
            try (FileChannel ch = FileChannel.open(
                    ndjsonFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            )) {
                ch.write(ByteBuffer.wrap(ndjsonBytes));
                ch.force(true);
            }
        } catch (IOException e) {
            throw new SinkException("Failed to write checkpoint to " + directory, e);
        }
    }

    @Override
    public String name() {
        return "FileSink";
    }

    public static String formatJson(CheckpointService.Checkpoint ckpt) {
        return "{\n" +
                "  \"treeSize\": " + ckpt.treeSize() + ",\n" +
                "  \"merkleRoot\": \"" + ckpt.merkleRoot() + "\",\n" +
                "  \"headHash\": \"" + ckpt.headHash() + "\",\n" +
                "  \"signature\": \"" + ckpt.signature() + "\",\n" +
                "  \"createdAtSec\": " + ckpt.createdAtSec() + "\n" +
                "}";
    }

    public static String formatNdjson(CheckpointService.Checkpoint ckpt) {
        return "{\"treeSize\":" + ckpt.treeSize() +
                ",\"merkleRoot\":\"" + ckpt.merkleRoot() + "\"" +
                ",\"headHash\":\"" + ckpt.headHash() + "\"" +
                ",\"signature\":\"" + ckpt.signature() + "\"" +
                ",\"createdAtSec\":" + ckpt.createdAtSec() + "}\n";
    }

    public static CheckpointService.Checkpoint parseJson(String json) {
        if (json == null) {
            throw new IllegalArgumentException("JSON string cannot be null");
        }
        Pattern pTreeSize = Pattern.compile("\"treeSize\"\\s*:\\s*(\\d+)");
        Pattern pRoot = Pattern.compile("\"merkleRoot\"\\s*:\\s*\"([^\"]*)\"");
        Pattern pHead = Pattern.compile("\"headHash\"\\s*:\\s*\"([^\"]*)\"");
        Pattern pSig = Pattern.compile("\"signature\"\\s*:\\s*\"([^\"]*)\"");
        Pattern pCreatedAt = Pattern.compile("\"createdAtSec\"\\s*:\\s*(\\d+)");

        Matcher mTree = pTreeSize.matcher(json);
        Matcher mRoot = pRoot.matcher(json);
        Matcher mHead = pHead.matcher(json);
        Matcher mSig = pSig.matcher(json);
        Matcher mCreated = pCreatedAt.matcher(json);

        if (!mTree.find() || !mRoot.find() || !mHead.find() || !mSig.find() || !mCreated.find()) {
            throw new IllegalArgumentException("Failed to parse checkpoint JSON: " + json);
        }

        long treeSize = Long.parseLong(mTree.group(1));
        String merkleRoot = mRoot.group(1);
        String headHash = mHead.group(1);
        String signature = mSig.group(1);
        long createdAtSec = Long.parseLong(mCreated.group(1));

        return new CheckpointService.Checkpoint(treeSize, merkleRoot, headHash, signature, createdAtSec);
    }
}
