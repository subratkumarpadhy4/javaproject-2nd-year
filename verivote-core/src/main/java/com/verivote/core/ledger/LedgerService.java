package com.verivote.core.ledger;

import java.util.List;
import java.util.Optional;

public interface LedgerService {

    record Appended(long height, String blockHash, String voteHash) {}

    Appended append(byte[] ciphertext, byte[] tokenHash, byte[] receiptHash);
    void markOrphan(long height);
    boolean isOrphaned(long height);
    long blockCount();
    long realVoteCount();
    long orphanCount();
    String headHash();
    Optional<Block> at(long height);
    List<Block> range(long fromIncl, long toExcl);
    byte[] rootAt(long treeSize);
    List<byte[]> proof(long height, long treeSize);
}
