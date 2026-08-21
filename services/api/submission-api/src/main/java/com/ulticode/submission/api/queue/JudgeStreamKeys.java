package com.ulticode.submission.api.queue;

/** Stable Redis Streams keys and group names for the Judge transport. */
public final class JudgeStreamKeys {

    private JudgeStreamKeys() {
    }

    public static final String JUDGE_DISPATCH_SEEN_PREFIX = "judge:{judge-stream}:dispatch:seen:";
    public static final String JUDGE_LEASE_PREFIX = "judge:lease:";
    public static final String JUDGE_STREAM_KEY = "judge:{judge-stream}:stream";
    public static final String LEGACY_JUDGE_STREAM_KEY = "judge:stream";
    public static final String JUDGE_STREAM_MIGRATION_LOCK_KEY = "judge:{judge-stream}:migration:lock";
    public static final String JUDGE_STREAM_DLQ_KEY = "judge:{judge-stream}:dlq";
    public static final String JUDGE_STREAM_DLQ_SEEN_PREFIX = "judge:{judge-stream}:dlq:seen:";
    public static final String JUDGE_STREAM_GROUP = "judge-workers";
    public static final long JUDGE_STREAM_VISIBILITY_TIMEOUT_MS = 1_800_000L;
}
