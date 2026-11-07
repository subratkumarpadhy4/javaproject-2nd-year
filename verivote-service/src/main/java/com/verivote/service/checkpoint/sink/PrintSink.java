package com.verivote.service.checkpoint.sink;

import com.verivote.service.checkpoint.CheckpointService;

/**
 * Prints checkpoint summaries to stdout for local debugging and demo purposes.
 * <p>
 * <b>NOTE:</b> This sink is NOT counted as an external durable sink and does NOT
 * satisfy the {@code minExternalSinks} quorum requirement.
 * </p>
 */
public class PrintSink implements ExternalSink {

    @Override
    public void write(CheckpointService.Checkpoint ckpt) throws SinkException {
        if (ckpt == null) {
            throw new IllegalArgumentException("Checkpoint cannot be null");
        }
        System.out.println("[PrintSink] Checkpoint: treeSize=" + ckpt.treeSize()
                + ", root=" + ckpt.merkleRoot()
                + ", head=" + ckpt.headHash()
                + ", sig=" + ckpt.signature()
                + ", created=" + ckpt.createdAtSec());
    }

    @Override
    public String name() {
        return "PrintSink";
    }

    @Override
    public boolean isExternal() {
        // Does NOT satisfy minExternalSinks
        return false;
    }
}
