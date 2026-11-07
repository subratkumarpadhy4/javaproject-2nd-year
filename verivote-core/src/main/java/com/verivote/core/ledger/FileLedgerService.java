package com.verivote.core.ledger;

import com.verivote.core.crypto.MerkleTree;
import com.verivote.core.crypto.Sha256;
import com.verivote.core.domain.DomainStrings;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.BigEndian;
import com.verivote.core.encoding.Hex;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class FileLedgerService implements LedgerService {

    private final Path ledgerFile;
    private final Path fallbackFile;
    private final List<Block> blocks = new ArrayList<>();
    private final List<byte[]> leaves = new ArrayList<>();
    private final Set<Long> orphanedHeights = new HashSet<>();

    public FileLedgerService(Path ledgerFile) {
        this.ledgerFile = ledgerFile;
        this.fallbackFile = ledgerFile.resolveSibling("orphans.fallback.ndjson");

        try {
            if (ledgerFile.getParent() != null) {
                Files.createDirectories(ledgerFile.getParent());
            }
            if (!Files.exists(ledgerFile)) {
                Files.createFile(ledgerFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize ledger file: " + ledgerFile, e);
        }

        LedgerFileParser.ParseResult result = LedgerFileParser.parse(ledgerFile);
        if (result.isFatal()) {
            throw new IllegalStateException("Fatal ledger error on load: " + result.fatal());
        }
        if (result.hasTornTail()) {
            LedgerFileParser.repairTorn(ledgerFile);
        }

        for (Block b : result.blocks()) {
            if (Block.VOTE.equals(b.type())) {
                if (b.ciphertextB64() == null) {
                    throw new IllegalStateException("Missing ciphertext for VOTE block at height " + b.height());
                }
                byte[] ct = Base64Codec.decode(b.ciphertextB64());
                byte[] computedHash = Sha256.hash(ct);
                String computedHashHex = Hex.encode(computedHash);
                if (!computedHashHex.equalsIgnoreCase(b.voteHash())) {
                    throw new IllegalStateException("Ciphertext hash mismatch at height " + b.height()
                            + ": computed " + computedHashHex + " != stored " + b.voteHash());
                }
                blocks.add(b);
                leaves.add(computeVoteLeaf(b.voteHash()));
            } else if (Block.ORPHAN.equals(b.type())) {
                blocks.add(b);
                orphanedHeights.add(b.refHeight());
                leaves.add(computeOrphanLeaf(b.refHeight()));
            }
        }
    }

    @Override
    public synchronized Appended append(byte[] ciphertext, byte[] tokenHash, byte[] receiptHash) {
        if (ciphertext == null || tokenHash == null || receiptHash == null) {
            throw new IllegalArgumentException("Append arguments cannot be null");
        }

        long height = blocks.size();
        String prevHash = (height == 0) ? Block.ZERO : blocks.get(blocks.size() - 1).blockHash();

        byte[] voteHashBytes = Sha256.hash(ciphertext);
        String voteHashHex = Hex.encode(voteHashBytes);
        String tokenHashHex = Hex.encode(tokenHash);
        String receiptHashHex = Hex.encode(receiptHash);
        String ciphertextB64 = Base64Codec.encode(ciphertext);
        long timestampSec = System.currentTimeMillis() / 1000;

        byte[] leaf = computeVoteLeaf(voteHashHex);
        leaves.add(leaf);

        byte[] rootBytes = MerkleTree.rootAt(leaves, leaves.size());
        String merkleRootHex = Hex.encode(rootBytes);

        Block blockWithoutHash = new Block(
                height,
                prevHash,
                Block.VOTE,
                -1L,
                voteHashHex,
                tokenHashHex,
                receiptHashHex,
                ciphertextB64,
                timestampSec,
                merkleRootHex,
                ""
        );
        String blockHashHex = Hex.encode(BlockHash.compute(blockWithoutHash));
        Block fullBlock = new Block(
                height,
                prevHash,
                Block.VOTE,
                -1L,
                voteHashHex,
                tokenHashHex,
                receiptHashHex,
                ciphertextB64,
                timestampSec,
                merkleRootHex,
                blockHashHex
        );

        writeBlockToDisk(fullBlock);
        blocks.add(fullBlock);

        return new Appended(height, blockHashHex, voteHashHex);
    }

    @Override
    public synchronized void markOrphan(long height) {
        if (isOrphaned(height)) {
            return;
        }

        long currentCount = blocks.size();
        String prevHash = (currentCount == 0) ? Block.ZERO : blocks.get(blocks.size() - 1).blockHash();
        long timestampSec = System.currentTimeMillis() / 1000;

        byte[] leaf = computeOrphanLeaf(height);
        leaves.add(leaf);

        byte[] rootBytes = MerkleTree.rootAt(leaves, leaves.size());
        String merkleRootHex = Hex.encode(rootBytes);

        Block blockWithoutHash = new Block(
                currentCount,
                prevHash,
                Block.ORPHAN,
                height,
                Block.ZERO,
                Block.ZERO,
                Block.ZERO,
                null,
                timestampSec,
                merkleRootHex,
                ""
        );
        String blockHashHex = Hex.encode(BlockHash.compute(blockWithoutHash));
        Block fullBlock = new Block(
                currentCount,
                prevHash,
                Block.ORPHAN,
                height,
                Block.ZERO,
                Block.ZERO,
                Block.ZERO,
                null,
                timestampSec,
                merkleRootHex,
                blockHashHex
        );

        try {
            writeBlockToDisk(fullBlock);
        } catch (Exception e) {
            // Durability fallback
            try {
                String line = LedgerFileParser.toJson(fullBlock) + "\n";
                Files.writeString(fallbackFile, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {}
            throw new IllegalStateException("Failed to append orphan block to ledger; fallback written", e);
        }

        blocks.add(fullBlock);
        orphanedHeights.add(height);
    }

    @Override
    public synchronized boolean isOrphaned(long height) {
        return orphanedHeights.contains(height);
    }

    @Override
    public synchronized long blockCount() {
        return blocks.size();
    }

    @Override
    public synchronized long realVoteCount() {
        return blocks.stream()
                .filter(b -> Block.VOTE.equals(b.type()) && !orphanedHeights.contains(b.height()))
                .count();
    }

    @Override
    public synchronized long orphanCount() {
        return blocks.stream()
                .filter(b -> Block.ORPHAN.equals(b.type()))
                .count();
    }

    @Override
    public synchronized String headHash() {
        if (blocks.isEmpty()) {
            return Block.ZERO;
        }
        return blocks.get(blocks.size() - 1).blockHash();
    }

    @Override
    public synchronized Optional<Block> at(long height) {
        if (height < 0 || height >= blocks.size()) {
            return Optional.empty();
        }
        return Optional.of(blocks.get((int) height));
    }

    @Override
    public synchronized List<Block> range(long fromIncl, long toExcl) {
        if (fromIncl < 0) {
            fromIncl = 0;
        }
        if (toExcl > blocks.size()) {
            toExcl = blocks.size();
        }
        if (fromIncl >= toExcl) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(blocks.subList((int) fromIncl, (int) toExcl)));
    }

    @Override
    public synchronized byte[] rootAt(long treeSize) {
        if (treeSize == 0) {
            return MerkleTree.EMPTY_ROOT;
        }
        if (treeSize > leaves.size() || treeSize < 0) {
            throw new IllegalArgumentException("Invalid treeSize: " + treeSize);
        }
        return MerkleTree.rootAt(leaves.subList(0, (int) treeSize), (int) treeSize);
    }

    @Override
    public synchronized List<byte[]> proof(long height, long treeSize) {
        if (treeSize <= 0 || treeSize > leaves.size()) {
            throw new IllegalArgumentException("Invalid treeSize: " + treeSize);
        }
        return MerkleTree.proof(leaves.subList(0, (int) treeSize), (int) height, (int) treeSize);
    }

    private void writeBlockToDisk(Block block) {
        String line = LedgerFileParser.toJson(block) + "\n";
        byte[] lineBytes = line.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(ledgerFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            channel.write(ByteBuffer.wrap(lineBytes));
            channel.force(true);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write block to disk", e);
        }
    }

    private static byte[] computeVoteLeaf(String voteHashHex) {
        return MerkleTree.leafHash(Hex.decode(voteHashHex));
    }

    private static byte[] computeOrphanLeaf(long refHeight) {
        byte[] prefix = DomainStrings.ORPHAN_PREFIX.getBytes(StandardCharsets.US_ASCII);
        byte[] ref = BigEndian.u64be(refHeight);
        byte[] orphanData = new byte[prefix.length + ref.length];
        System.arraycopy(prefix, 0, orphanData, 0, prefix.length);
        System.arraycopy(ref, 0, orphanData, prefix.length, ref.length);
        return MerkleTree.leafHash(orphanData);
    }
}
