package com.ulticode.submission.api.queue;

import java.util.Optional;

/**
 * Provider-owned queue contract shared by the Submission producer and Judge
 * consumer. Broker-specific retry and acknowledgement details stay in
 * adapters; the wire envelope and operation semantics stay here.
 */
public interface JudgeQueue {

    /** Enqueue a job idempotently by submission and generation. */
    void enqueue(JudgeJobEnvelope envelope);

    /** Poll a job, retaining broker ownership until ack or nack. */
    Optional<JudgeJobHandle> poll(long timeoutMillis);

    /** Acknowledge successful processing. */
    void ack(JudgeJobHandle handle);

    /** Leave or dead-letter a failed job according to the adapter policy. */
    void nack(JudgeJobHandle handle, String reason);

    /** Return the broker-side pending depth used by operational checks. */
    long pendingDepth();
}
