package com.ulticode.common.redis;

/** Logical Redis workload roles used by owner client/config seams. */
public enum RedisWorkloadRole {
    STREAMS,
    CACHE,
    RATE_LIMIT,
    REPLAY,
    QUEUE,
    JUDGE,
    PUBSUB
}
