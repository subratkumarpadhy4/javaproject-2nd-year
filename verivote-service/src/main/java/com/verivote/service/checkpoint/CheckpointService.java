package com.verivote.service.checkpoint;

import java.util.List;
import java.util.Optional;

public interface CheckpointService {

    record Checkpoint(
            long treeSize,
            String merkleRoot,
            String headHash,
            String signature,
            long createdAtSec
    ) {}

    /**
     * Force a checkpoint now (idempotent — returns latest if no new blocks).
     */
    Optional<Checkpoint> forceNow();

    /**
     * Called by timer; checkpoints if >= min-blocks since last, or >= min-seconds with new blocks.
     */
    void maybeCheckpoint();

    Optional<Checkpoint> latest();

    List<Checkpoint> all();
}
