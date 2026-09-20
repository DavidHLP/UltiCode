package com.ulticode.submission.api.queue;

import java.io.Serializable;

/** Opaque broker handle returned by a queue adapter and consumed by ack/nack. */
public record JudgeJobHandle(JudgeJobEnvelope envelope, Object ackToken) implements Serializable {

    public JudgeJobHandle(JudgeJobEnvelope envelope) {
        this(envelope, null);
    }
}
