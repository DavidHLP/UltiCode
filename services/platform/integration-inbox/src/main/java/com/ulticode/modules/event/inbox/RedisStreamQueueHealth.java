package com.ulticode.modules.event.inbox;

import com.ulticode.common.metrics.WorkerSloMeters;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Shared best-effort observation of one Redis Streams consumer group. */
public final class RedisStreamQueueHealth {

    private static final String XINFO = "XINFO";

    private final StringRedisTemplate redisTemplate;

    public RedisStreamQueueHealth(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    }

    public Snapshot observe(String streamKey, String group, String dlqKey) {
        StreamOperations<String, String, String> streams;
        try {
            streams = redisTemplate.opsForStream();
        } catch (RuntimeException ignored) {
            return unknown();
        }
        GroupSnapshot groupSnapshot = observeGroup(streamKey, group);
        return new Snapshot(
                groupSnapshot.queueLag(),
                groupSnapshot.pelSize(),
                oldestPendingAgeSeconds(streams, streamKey, group),
                dlqSize(streams, dlqKey));
    }

    private Snapshot unknown() {
        return new Snapshot(
                WorkerSloMeters.UNKNOWN,
                WorkerSloMeters.UNKNOWN,
                WorkerSloMeters.UNKNOWN,
                WorkerSloMeters.UNKNOWN);
    }

    private GroupSnapshot observeGroup(String streamKey, String group) {
        try {
            Object reply = redisTemplate.execute(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection ->
                            connection.execute(XINFO, "GROUPS".getBytes(StandardCharsets.UTF_8),
                                    streamKey.getBytes(StandardCharsets.UTF_8)));
            return new GroupSnapshot(
                    findGroupField(reply, group, "lag"),
                    findGroupField(reply, group, "pending"));
        } catch (RuntimeException ignored) {
            return new GroupSnapshot(WorkerSloMeters.UNKNOWN, WorkerSloMeters.UNKNOWN);
        }
    }

    private long oldestPendingAgeSeconds(
            StreamOperations<String, String, String> streams, String streamKey, String group) {
        try {
            PendingMessages pending = streams.pending(streamKey, group, Range.unbounded(), 1);
            if (pending == null || pending.isEmpty()) {
                return 0L;
            }
            PendingMessage oldest = pending.iterator().next();
            Duration elapsed = oldest.getElapsedTimeSinceLastDelivery();
            return elapsed == null ? WorkerSloMeters.UNKNOWN : Math.max(0L, elapsed.getSeconds());
        } catch (RuntimeException ignored) {
            return WorkerSloMeters.UNKNOWN;
        }
    }

    private long dlqSize(StreamOperations<String, String, String> streams, String dlqKey) {
        if (dlqKey == null || dlqKey.isBlank()) {
            return WorkerSloMeters.UNKNOWN;
        }
        try {
            Long size = streams.size(dlqKey);
            return size == null ? WorkerSloMeters.UNKNOWN : size;
        } catch (RuntimeException ignored) {
            return WorkerSloMeters.UNKNOWN;
        }
    }

    private long findGroupField(Object reply, String group, String fieldName) {
        if (!(reply instanceof List<?> entries)) {
            return WorkerSloMeters.UNKNOWN;
        }
        for (Object entry : entries) {
            if (entry instanceof List<?> fields && group.equals(field(fields, "name"))) {
                return number(fields, fieldName);
            }
        }
        if (group.equals(field(entries, "name"))) {
            return number(entries, fieldName);
        }
        return WorkerSloMeters.UNKNOWN;
    }

    private String field(List<?> fields, String name) {
        for (int i = 0; i + 1 < fields.size(); i += 2) {
            if (name.equals(text(fields.get(i)))) {
                return text(fields.get(i + 1));
            }
        }
        return null;
    }

    private long number(List<?> fields, String name) {
        String value = field(fields, name);
        if (value == null) {
            return WorkerSloMeters.UNKNOWN;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return WorkerSloMeters.UNKNOWN;
        }
    }

    private String text(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? null : String.valueOf(value);
    }

    public record Snapshot(long queueLag, long pelSize, long oldestPendingAgeSeconds, long dlqSize) {
    }

    private record GroupSnapshot(long queueLag, long pelSize) {
    }
}
