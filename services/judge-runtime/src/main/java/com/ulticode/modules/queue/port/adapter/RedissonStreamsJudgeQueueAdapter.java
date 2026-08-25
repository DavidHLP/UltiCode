package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.metrics.WorkerSloMeters;
import com.ulticode.submission.api.queue.JudgeStreamKeys;
import com.ulticode.submission.api.queue.JudgeJobEnvelope;
import com.ulticode.submission.api.queue.JudgeJobHandle;
import com.ulticode.submission.api.queue.JudgeQueue;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.PendingEntry;
import org.redisson.api.stream.PendingResult;
import org.redisson.api.stream.StreamCreateGroupArgs;
import org.redisson.api.stream.StreamGroup;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.api.stream.StreamReadGroupArgs;
import org.redisson.client.codec.StringCodec;
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
 * {@code judge:{judge-stream}:dispatch:seen:{submissionId}:{generation}} short-circuits
 * repeat dispatches. The same key is reused by the M3a shadow comparator.
 *
 * <p>One shared consumer group can contain consumers from multiple Judge Worker
 * JVMs. The handle's
 * {@link JudgeJobHandle#ackToken()} is the Redisson {@code StreamMessageId}
 * (kept as {@code Object} so the port package stays broker-agnostic per
 * the ADR-002 hex-arch rule).
 *
 * <p>Only active when {@code app.features.judge-queue.use-port=true}.
 *
 * <p><strong>Codec contract:</strong> this adapter explicitly uses
 * {@link StringCodec} for both the RScript calls and the RStream, regardless
 * of the client-wide Redisson config codec (default Kryo5). The Lua scripts
 * write literal plain-text field names ({@code payload}, {@code sourceId},
 * ...) and pass plain control arguments (TTL seconds, XACK group), so every
 * read/write of the stream and every script ARGV must stay plain text. The
 * generic {@code RStream<String, String>} type does not select the codec;
 * without this explicit {@code StringCodec.INSTANCE} the default Kryo5 codec
 * would corrupt script ARGV (EX/XACK reject binary) and fail to decode the
 * literal XADD fields. Legacy RQueue usage keeps the client-wide codec.
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.features.judge-queue.use-port",
        havingValue = "true")
public class RedissonStreamsJudgeQueueAdapter implements JudgeQueue {

    private static final String ATOMIC_ENQUEUE_SCRIPT = """
            local claimed = redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2])
            if not claimed then
                return 0
            end
            local added = redis.pcall('XADD', KEYS[2], '*', 'payload', ARGV[3])
            if type(added) == 'table' and added['err'] then
                redis.call('DEL', KEYS[1])
                return -1
            end
            return 1
            """;

    private static final String ATOMIC_DEAD_LETTER_SCRIPT = """
            local marked = redis.call('SET', KEYS[3], '1', 'NX', 'EX', ARGV[6])
            if marked then
                local added = redis.pcall('XADD', KEYS[2], '*',
                    'payload', ARGV[1],
                    'sourceId', ARGV[2],
                    'deliveryCount', ARGV[3],
                    'consumer', ARGV[4],
                    'reason', ARGV[5])
                if type(added) == 'table' and added['err'] then
                    redis.call('DEL', KEYS[3])
                    return -1
                end
            end
            redis.call('XACK', KEYS[1], ARGV[7], ARGV[2])
            return 1
            """;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final String streamKey;
    private final String groupName;
    private final String consumerId;
    private final long visibilityTimeoutMs;
    private final int maxDeliveryAttempts;
    /** Nullable so unit tests without a registry still compile. */
    private final MeterRegistry meterRegistry;

    /**
     * Review 2026-08-25 P1: queue/consumer SLO meters for the Streams path.
     * Null when no registry is present (unit tests); refreshed by the
     * unacked-reaper sweep, success/failure marked by {@link #ack}/poll.
     */
    private WorkerSloMeters sloMeters;

    @jakarta.annotation.PostConstruct
    void initSloMeters() {
        this.sloMeters = meterRegistry == null
                ? null : WorkerSloMeters.register(meterRegistry, "judge.streams");
    }

    /**
     * Create the consumer group on startup, or recreate it after NOGROUP
     * recovery (idempotent; safe to call when the group already exists).
     *
     * <p>The group is always created at {@code 0-0}
     * ({@link StreamMessageId#ALL}), never at {@code $} (NEWEST). If the
     * group disappears while stream entries remain (e.g. the stream key was
     * evicted/recreated or a operator deleted the group), a NEWEST group
     * would only see future entries and every already-enqueued job would be
     * skipped while its outbox row is already SENT. Creating from {@code 0-0}
     * replays pre-group entries; replay is idempotent because stale
     * generations are dropped by the judge fence CAS.
     */
    @PostConstruct
    public void ensureGroup() {
        RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
        if (!stream.isExists()) {
            // Redisson 4.3.1's StreamCreateGroupArgs has no MKSTREAM flag and
            // XGROUP CREATE fails with "no such key" before the first dispatch
            // created the stream (fresh deploy). Bootstrap the key with a
            // no-op entry, then delete it — the empty stream key stays alive,
            // so the group creation below succeeds.
            StreamMessageId noop = stream.add(
                    org.redisson.api.stream.StreamAddArgs.entry("__noop", "1"));
            stream.remove(noop);
            log.info("Bootstrapped empty stream {} for consumer group creation", streamKey);
        }
        List<StreamGroup> groups = stream.listGroups();
        boolean exists = groups.stream().anyMatch(g -> groupName.equals(g.getName()));
        if (!exists) {
            try {
                stream.createGroup(StreamCreateGroupArgs.name(groupName)
                        .id(StreamMessageId.ALL));
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
        // SETNX + XADD must be one Redis operation. Otherwise a process crash
        // after SETNX leaves a permanent dedup marker and the outbox dispatcher
        // can mark a job SENT without a corresponding Stream entry.
        String dedupKey = JudgeStreamKeys.JUDGE_DISPATCH_SEEN_PREFIX
                + envelope.submissionId() + ":" + envelope.generation();
        long ttlSeconds = Math.max(1L, visibilityTimeoutMs * 5L / 1000L);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JudgeJobEnvelope", e);
        }

        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                ATOMIC_ENQUEUE_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(dedupKey, streamKey),
                "1",
                Long.toString(ttlSeconds),
                payload);
        if (result != null && result == 0L) {
            log.debug("Streams enqueue dedup: skipping repeat for {} gen {}",
                    envelope.submissionId(), envelope.generation());
            return;
        }
        if (result == null || result < 0L) {
            throw new IllegalStateException("Failed to atomically enqueue JudgeJobEnvelope");
        }
    }

    @Override
    public Optional<JudgeJobHandle> poll(long timeoutMillis) {
        RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
        // XREADGROUP > for new entries. count(1) so the worker holds at
        // most one job at a time per consumer.
        StreamReadGroupArgs readArgs = StreamReadGroupArgs.neverDelivered().count(1);
        if (timeoutMillis > 0) {
            // Redis BLOCK 0 means wait forever; omit BLOCK for the port's
            // documented non-blocking poll(0) contract.
            readArgs.timeout(Duration.ofMillis(timeoutMillis));
        }
        Map<StreamMessageId, Map<String, String>> entries;
        try {
            entries = stream.readGroup(groupName, consumerId, readArgs);
        } catch (Exception e) {
            if (!isNoGroup(e)) {
                markConsumeFailure();
                throw e;
            }
            ensureGroup();
            entries = stream.readGroup(groupName, consumerId, readArgs);
        }
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
            markConsumeFailure();
            return Optional.empty();
        }
        return Optional.of(new JudgeJobHandle(envelope, first.getKey()));
    }
    private boolean isNoGroup(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains("NOGROUP")) {
                return true;
            }
        }
        return false;
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
        try {
            redissonClient.getStream(streamKey, StringCodec.INSTANCE).ack(groupName, id);
            if (sloMeters != null) {
                sloMeters.markSuccess();
            }
        } catch (RuntimeException e) {
            markConsumeFailure();
            throw e;
        }
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
     * inspector (via the {@link com.ulticode.submission.api.queue.JudgeQueue#pendingDepth()}
     * port method) to normalize monitoring depth across backends.
     */
    public long pendingCount() {
        RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
        PendingResult info = stream.getPendingInfo(groupName);
        return info == null ? 0L : info.getTotal();
    }

    /** SLO meters owned by this adapter; null without a MeterRegistry (tests). */
    public WorkerSloMeters sloMeters() {
        return sloMeters;
    }

    /**
     * Group lag from {@code XINFO GROUPS} via Redisson's mapped group info
     * ({@code getLag()}, Redis >= 7); {@link WorkerSloMeters#UNKNOWN} when the
     * broker cannot answer or the group is absent.
     */
    public long streamLag() {
        try {
            RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
            for (org.redisson.api.stream.StreamGroup group : stream.listGroups()) {
                if (groupName.equals(group.getName())) {
                    return group.getLag();
                }
            }
            return WorkerSloMeters.UNKNOWN;
        } catch (RuntimeException e) {
            log.debug("Judge stream lag unavailable: {}", e.getMessage());
            return WorkerSloMeters.UNKNOWN;
        }
    }

    /**
     * Idle time of the oldest PEL entry (ms), 0 when the PEL is empty,
     * {@link WorkerSloMeters#UNKNOWN} when it cannot be observed.
     */
    public long oldestPendingIdleMs() {
        try {
            RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
            List<PendingEntry> oldest = stream.listPending(
                    groupName,
                    StreamMessageId.MIN, StreamMessageId.MAX,
                    0L, java.util.concurrent.TimeUnit.MILLISECONDS, 1);
            if (oldest == null || oldest.isEmpty()) {
                return 0L;
            }
            Long idle = oldest.get(0).getIdleTime();
            return idle == null ? WorkerSloMeters.UNKNOWN : Math.max(0L, idle);
        } catch (RuntimeException e) {
            log.debug("Judge PEL age unavailable: {}", e.getMessage());
            return WorkerSloMeters.UNKNOWN;
        }
    }

    /** Dead-letter stream depth (XLEN of the DLQ key). */
    public long dlqSize() {
        try {
            RStream<String, String> dlq = redissonClient.getStream(
                    JudgeStreamKeys.JUDGE_STREAM_DLQ_KEY, StringCodec.INSTANCE);
            return dlq.isExists() ? dlq.size() : 0L;
        } catch (RuntimeException e) {
            log.debug("Judge DLQ size unavailable: {}", e.getMessage());
            return WorkerSloMeters.UNKNOWN;
        }
    }

    private void markConsumeFailure() {
        if (sloMeters != null) {
            sloMeters.incrementFailures();
        }
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
        RStream<String, String> stream = redissonClient.getStream(streamKey, StringCodec.INSTANCE);
        PendingResult pendingInfo = stream.getPendingInfo(groupName);
        if (pendingInfo == null || pendingInfo.getTotal() == 0) {
            return Optional.empty();
        }
        // List pending entries idle longer than minIdleMs (XPENDING IDLE
        // filter), oldest first. Filtering by idle time — instead of
        // inspecting only the single oldest entry — prevents a recently
        // re-claimed entry with the oldest ID from starving genuinely stale
        // entries behind it (each XCLAIM resets the idle clock on the entry
        // it touches).
        List<PendingEntry> pending = stream.listPending(
                groupName,
                StreamMessageId.MIN, StreamMessageId.MAX,
                minIdleMs, java.util.concurrent.TimeUnit.MILLISECONDS, 1);
        if (pending.isEmpty()) {
            return Optional.empty();
        }
        PendingEntry first = pending.get(0);
        if (first.getDeliveryCount() >= Math.max(1, maxDeliveryAttempts)) {
            // Budget exhausted BEFORE any XCLAIM: each XCLAIM increments the
            // broker delivery counter, so claiming here would burn an attempt
            // on a pure ownership hand-off (e.g. the worker fills up between
            // the reaper's capacity check and this claim, and the handle is
            // nacked without a judge run). Read the payload read-only and
            // dead-letter directly.
            Map<StreamMessageId, Map<String, String>> entry = stream.range(
                    1, first.getId(), first.getId());
            if (entry != null && !entry.isEmpty()) {
                deadLetter(entry.entrySet().iterator().next(), first.getDeliveryCount());
            } else {
                // Entry was trimmed/removed; just clear the PEL reference.
                stream.ack(groupName, first.getId());
            }
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

    private void deadLetter(Map.Entry<StreamMessageId, Map<String, String>> claimedEntry,
                            long deliveryCount) {
        String sourceId = claimedEntry.getKey().toString();
        String markerKey = JudgeStreamKeys.JUDGE_STREAM_DLQ_SEEN_PREFIX + sourceId;
        Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                ATOMIC_DEAD_LETTER_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(streamKey, JudgeStreamKeys.JUDGE_STREAM_DLQ_KEY, markerKey),
                claimedEntry.getValue().getOrDefault("payload", ""),
                sourceId,
                Long.toString(deliveryCount),
                consumerId,
                "max-delivery-attempts",
                Long.toString(Math.max(3600L, visibilityTimeoutMs / 1000L)),
                groupName);
        if (result == null || result < 0L) {
            throw new IllegalStateException("Failed to atomically dead-letter judge Stream entry " + sourceId);
        }
        incrementDeadLetterCounter();
        log.error("Dead-lettered judge Stream entry id {} after {} delivery attempts",
                claimedEntry.getKey(), deliveryCount);
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

    private void incrementDeadLetterCounter() {
        if (meterRegistry != null) {
            meterRegistry.counter("judge.streams.dlq").increment();
        }
    }
}
