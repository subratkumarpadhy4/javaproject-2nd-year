package com.verivote.service.checkpoint.sink;

import com.verivote.service.checkpoint.CheckpointService;

public interface ExternalSink {

    /**
     * Write the checkpoint to durable external storage. Must fsync.
     */
    void write(CheckpointService.Checkpoint ckpt) throws SinkException;

    String name();

    default boolean isExternal() {
        return true;
    }

    class SinkException extends RuntimeException {
        public SinkException(String message) {
            super(message);
        }

        public SinkException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
