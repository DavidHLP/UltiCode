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
     * {@code judge:{judge-stream}:dispatch:seen:{submissionId}:{generation}}.
     * The marker and Stream append are committed by one Redis Lua operation;
     * the shared hash tag keeps that script Redis Cluster-safe.
     */
    public static final String JUDGE_DISPATCH_SEEN_PREFIX = "judge:{judge-stream}:dispatch:seen:";

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
     *
     * <p>The {@code {judge-stream}} hash tag keeps this key in the same
     * Redis Cluster slot as the dedup / DLQ keys used inside the atomic Lua
     * scripts. The pre-extraction key {@code judge:stream} had no hash tag
     * and is drained by {@code JudgeStreamLegacyMigration} on upgrade.
     */
    public static final String JUDGE_STREAM_KEY = "judge:{judge-stream}:stream";

    /**
     * Pre-extraction stream key ({@code judge:stream}) written by the
     * fused App before the judge-runtime cutover. Entries on this key (and
     * its consumer-group PEL) are drained into {@link #JUDGE_STREAM_KEY} by
     * {@code JudgeStreamLegacyMigration} once, then the key is deleted.
     */
    public static final String LEGACY_JUDGE_STREAM_KEY = "judge:stream";

    /** One-shot SETNX lock guarding the legacy stream drain across judge instances. */
    public static final String JUDGE_STREAM_MIGRATION_LOCK_KEY = "judge:{judge-stream}:migration:lock";

    /** Stream receiving entries that exhausted the broker retry budget. */
    public static final String JUDGE_STREAM_DLQ_KEY = "judge:{judge-stream}:dlq";

    /** Idempotency markers for the atomic DLQ append/ACK operation. */
    public static final String JUDGE_STREAM_DLQ_SEEN_PREFIX = "judge:{judge-stream}:dlq:seen:";

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
