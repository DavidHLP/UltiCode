package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.queue.redis.JudgeStreamKeys;
import com.ulticode.modules.queue.port.JudgeJobEnvelope;
import com.ulticode.modules.queue.port.JudgeJobHandle;
import com.ulticode.modules.queue.port.JudgeQueue;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.PendingEntry;
import org.redisson.api.stream.PendingResult;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamGroup;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redisson Streams {@link JudgeQueue} adapter (ADR-003 M3c-2, §2.4 / §2.6
 * F6). Backs the port with Redis 7 Streams + a single consumer group
 * ({@code judge-workers}). Each envelope is a single Stream entry whose
 * {@code payload} field carries a JSON-serialized {@link JudgeJobEnvelope}.
 * Workers consume via {@code XREADGROUP} and ack via {@code XACK} — the
 * broker (not the worker) owns retry semantics, which fixes the legacy
 * destructive {@code RQueue.poll} problem (F6).
 *
 * <p>Idempotent enqueue: a SETNX on
 * {@code judge:dispatch:seen:{submissionId}:{generation}} short-circuits
 * repeat dispatches. The same key is reused by the M3a shadow comparator.
 *
 * <p>One shared consumer group can contain consumers from multiple Judge Worker
 * JVMs. The handle's
 * {@link JudgeJobHandle#ackToken()} is the Redisson {@code StreamMessageId}
 * (kept as {@code Object} so the port package stays broker-agnostic per
 * the ADR-002 hex-arch rule).
 *
 * <p>Only active when {@code app.features.judge-queue.use-port=true}.
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.features.judge-queue.use-port",
        havingValue = "true")
public class RedissonStreamsJudgeQueueAdapter implements JudgeQueue {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final String streamKey;
    private final String groupName;
    private final String consumerId;
    private final long visibilityTimeoutMs;
    /** Nullable so unit tests without a registry still compile. */
    private final MeterRegistry meterRegistry;

    /**
     * Create the consumer group on startup (idempotent; safe to call when
     * the group already exists).
     */
    @PostConstruct
    public void ensureGroup() {
        RStream<String, String> stream = redissonClient.getStream(streamKey);
        List<StreamGroup> groups = stream.listGroups();
        boolean exists = groups.stream().anyMatch(g -> groupName.equals(g.getName()));
        if (!exists) {
            try {
                stream.createGroup(StreamCreateGroupArgs.name(groupName));
                log.info("Created Redis Streams consumer group {} on {}", groupName, streamKey);
            } catch (Exception e) {
                // Race: another instance created the group between the
                // existence check and createGroup. Treat as success.
                if (stream.listGroups().stream().anyMatch(g -> groupName.equals(g.getName()))) {
                    log.debug("Consumer group {} already created by another instance", groupName);
                } else {
                    throw e;
                }
            }
        }
    }

    @Override
    public void enqueue(JudgeJobEnvelope envelope) {
        // Idempotency: SETNX via Redisson RBucket on the
        // (submissionId, generation) dedup key. TTL = visibility × 5
        // (longer than the worst-case reclaim cycle). Redisson's
        // setIfAbsent is atomic — no TOCTOU race.
        String dedupKey = JudgeStreamKeys.JUDGE_DISPATCH_SEEN_PREFIX
                + envelope.submissionId() + ":" + envelope.generation();
        RBucket<String> bucket = redissonClient.getBucket(dedupKey);
        long ttlSeconds = Math.max(1L, visibilityTimeoutMs * 5L / 1000L);
        boolean weSet = bucket.setIfAbsent("1", Duration.ofSeconds(ttlSeconds));
        if (!weSet) {
            log.debug("Streams enqueue dedup: skipping repeat for {} gen {}",
                    envelope.submissionId(), envelope.generation());
            return;
        }

        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            // Roll back the dedup key so a future retry can succeed.
            bucket.delete();
            throw new IllegalStateException("Failed to serialize JudgeJobEnvelope", e);
        }
        try {
            redissonClient.getStream(streamKey).add(
                    org.redisson.api.stream.StreamAddArgs.entry("payload", payload));
        } catch (Exception e) {
            // codex P1 #2 fix: SETNX succeeded above; if stream.add fails
            // (Redis down, stream full, etc.) the dedup key is in place
            // and a future retry will see the row as "already delivered"
            // and short-circuit — silently losing the message. Roll the
            // dedup key back so the next dispatcher sweep re-enqueues.
            bucket.delete();
            throw e;
        }
    }

    @Override
    public Optional<JudgeJobHandle> poll(long timeoutMillis) {
        RStream<String, String> stream = redissonClient.getStream(streamKey);
        // XREADGROUP > for new entries. count(1) so the worker holds at
        // most one job at a time per consumer.
        Map<StreamMessageId, Map<String, String>> entries = stream.readGroup(
                groupName, consumerId,
                StreamReadGroupArgs.neverDelivered()
                        .count(1)
                        .timeout(Duration.ofMillis(Math.max(timeoutMillis, 0L))));
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<StreamMessageId, Map<String, String>> first =
                entries.entrySet().iterator().next();
        JudgeJobEnvelope envelope = decode(first.getValue());
        if (envelope == null) {
            // Poison message: ack immediately so the broker doesn't keep
            // redelivering. Logged at WARN for ops to inspect.
            log.warn("Streams poison message: failed to decode payload at id {}; acking to skip",
                    first.getKey());
            stream.ack(groupName, first.getKey());
            incrementPoisonCounter();
            return Optional.empty();
        }
        return Optional.of(new JudgeJobHandle(envelope, first.getKey()));
    }

    @Override
    public void ack(JudgeJobHandle handle) {
        if (handle == null || handle.ackToken() == null) {
            return;
        }
        if (!(handle.ackToken() instanceof StreamMessageId id)) {
            log.warn("Streams ack: unexpected ackToken type {}; skipping",
                    handle.ackToken().getClass().getName());
            return;
        }
        redissonClient.getStream(streamKey).ack(groupName, id);
    }

    @Override
    public void nack(JudgeJobHandle handle, String reason) {
        // Leave the entry in the PEL. The unacked reaper
        // (UnackedStreamEntriesReaper) will XCLAIM it after
        // visibilityTimeoutMs and route the reclaimed handle to the worker.
        // No re-enqueue — that
        // would create a duplicate Stream entry and the dedup SETNX would
        // (correctly) reject it, but we'd still be paying for the write.
        log.debug("Streams nack: leaving id {} in PEL (reason: {})",
                handle.ackToken(), reason);
    }

    /**
     * Pending entries count (XPENDING). Used by the unacked reaper to
     * drive the {@code judge.streams.pending} gauge, and by the queue
     * inspector (via the {@link com.ulticode.modules.queue.port.JudgeQueue#pendingDepth()}
     * port method) to normalize monitoring depth across backends.
     */
    public long pendingCount() {
        RStream<String, String> stream = redissonClient.getStream(streamKey);
        PendingResult info = stream.getPendingInfo(groupName);
        return info == null ? 0L : info.getTotal();
    }

    @Override
    public long pendingDepth() {
        // Same XPENDING total as pendingCount(); the port method exists so
        // monitoring can read the Stream-backed depth through the JudgeQueue
        // seam without downcasting to the concrete adapter.
        return pendingCount();
    }

    /**
     * XCLAIM entries idle for more than {@code minIdleMs} to this
     * consumer, then read one of them off this consumer's PEL so the
     * caller can re-process. Returns at most one entry per call — the
     * reaper's {@code fixedDelay} paces the loop so the natural drain
     * rate equals the visibility timeout.
     */
    public Optional<JudgeJobHandle> claimIdle(long minIdleMs) {
        RStream<String, String> stream = redissonClient.getStream(streamKey);
        if (stream.getPendingInfo(groupName).getTotal() == 0) {
            return Optional.empty();
        }
        // List a window of pending entries; filter by idle time; claim the
        // oldest one to this consumer; read it back via the PEL (id "0"
        // relative to this consumer).
        List<PendingEntry> pending = stream.listPending(
                groupName,
                StreamMessageId.MIN, StreamMessageId.MAX, 1);
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        PendingEntry first = pending.get(0);
        if (first.getIdleTime() < minIdleMs) {
            return Optional.empty();
        }
        // XCLAIM moves ownership. The claimed entries are returned in the
        // map; we use the first one and return it to the worker for
        // processing and eventual ack.
        Map<StreamMessageId, Map<String, String>> claimed = stream.claim(
                groupName, consumerId, minIdleMs, java.util.concurrent.TimeUnit.MILLISECONDS,
                first.getId());
        if (claimed == null || claimed.isEmpty()) {
            return Optional.empty();
        }
        Map.Entry<StreamMessageId, Map<String, String>> claimedEntry =
                claimed.entrySet().iterator().next();
        JudgeJobEnvelope envelope = decode(claimedEntry.getValue());
        if (envelope == null) {
            stream.ack(groupName, claimedEntry.getKey());
            return Optional.empty();
        }
        log.info("Reclaimed idle Streams entry id {} (>= {}ms idle, was idle {}ms)",
                claimedEntry.getKey(), minIdleMs, first.getIdleTime());
        return Optional.of(new JudgeJobHandle(envelope, claimedEntry.getKey()));
    }

    private JudgeJobEnvelope decode(Map<String, String> entry) {
        String json = entry.get("payload");
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, JudgeJobEnvelope.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to decode JudgeJobEnvelope from Streams entry: {}", e.getMessage());
            return null;
        }
    }

    private void incrementPoisonCounter() {
        if (meterRegistry != null) {
            meterRegistry.counter("judge.streams.poison").increment();
        }
    }
}
