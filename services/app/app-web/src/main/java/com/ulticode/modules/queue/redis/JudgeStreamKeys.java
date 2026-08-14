package com.ulticode.modules.queue.redis;

/**
 * Redis keys and configuration for the judge-stream dispatch path
 * (ADR-003 M3a/M3b/M3c-2).
 *
 * <p><strong>Deep module</strong> &mdash; extracted from the fused
 * {@code CacheConstants} (260 LOC, 9 unrelated namespaces). Only the
 * judge-stream constants were alive; the auth / business / rate-limit
 * constants and all 8 helper methods were unreferenced dead code and
 * have been deleted with the parent class.
 *
 * <p>Holds:
 * <ul>
 *   <li>{@link #JUDGE_STREAM_KEY} / {@link #JUDGE_STREAM_GROUP} / {@link #JUDGE_STREAM_VISIBILITY_TIMEOUT_MS}
 *       &mdash; the Redis Streams consumer configuration used by
 *       {@code QueueConfig} and {@code RedissonStreamsJudgeQueueAdapter}.</li>
 *   <li>{@link #JUDGE_DISPATCH_SEEN_PREFIX} &mdash; the shadow-comparator
 *       dedup prefix (ADR-003 M3a).</li>
 *   <li>{@link #JUDGE_LEASE_PREFIX} &mdash; reserved for a future Redis-backed
 *       heartbeat variant (ADR-003 M3b); current leases live in the
 *       {@code submissions.judging_lease_expires_at} column.</li>
 * </ul>
 */
public final class JudgeStreamKeys {

    private JudgeStreamKeys() {
    }

    /**
     * Judge dispatch seen-set prefix (ADR-003 M3a shadow comparator).
     * Tracks which {@code (submissionId, generation)} pairs have actually been
     * enqueued onto the active producer so the shadow outbox dispatcher can
     * diff against outbox rows. Format:
     * {@code judge:dispatch:seen:{submissionId}:{generation}}.
     */
    public static final String JUDGE_DISPATCH_SEEN_PREFIX = "judge:dispatch:seen:";

    /**
     * Judge lease Redis prefix (ADR-003 M3b). Reserved for a future Redis-backed
     * heartbeat variant; the current implementation keeps leases in the
     * {@code submissions.judging_lease_expires_at} column. Format:
     * {@code judge:lease:{submissionId}:{attemptId}}.
     */
    public static final String JUDGE_LEASE_PREFIX = "judge:lease:";

    /**
     * Redis Streams key for judge job dispatches (ADR-003 M3c-2). A
     * single stream carries both v1 (legacy) and v2 (fence-aware) envelopes;
     * the {@code version} field on each entry discriminates.
     */
    public static final String JUDGE_STREAM_KEY = "judge:stream";

    /**
     * Consumer group on the {@link #JUDGE_STREAM_KEY} stream. One shared group
     * distributes entries across independently deployed Judge Worker consumers.
     */
    public static final String JUDGE_STREAM_GROUP = "judge-workers";

    /**
     * Visibility timeout (ms) for the M3c-2 Streams adapter. After this
     * many ms without an {@code XACK}, the unacked reaper will
     * {@code XCLAIM} the entry.
     *
     * <p>Must exceed the worst-case in-flight judge duration (sandbox hard
     * timeout is capped at {@code MAX_BATCH_HARD_TIMEOUT_SECONDS=180s} plus
     * compile budget and scheduling slack). 30 minutes keeps slow jobs owned
     * by their original worker; crash recovery is handled in seconds by the
     * DB-side {@code JudgingLeaseReaper} (generation bump + fresh outbox
     * row), so the stream reaper only serves as the final backstop and PEL
     * sweeper. Also drives the dedup-key TTL ({@code visibility x 5}) in
     * {@code RedissonStreamsJudgeQueueAdapter#enqueue}.
     */
    public static final long JUDGE_STREAM_VISIBILITY_TIMEOUT_MS = 1_800_000L;
}
