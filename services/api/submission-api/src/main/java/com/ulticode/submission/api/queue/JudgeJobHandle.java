package com.ulticode.submission.api.queue;

/** Opaque broker handle returned by a queue adapter and consumed by ack/nack. */
public record JudgeJobHandle(JudgeJobEnvelope envelope, Object ackToken) {

    public JudgeJobHandle(JudgeJobEnvelope envelope) {
        this(envelope, null);
    }
}
