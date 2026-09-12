package com.ulticode.modules.event.inbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Common Redis Streams group/read/reclaim/ack transport mechanics. */
public final class RedisStreamTransport {

    private final StringRedisTemplate redisTemplate;
    private final String streamKey;
    private final String group;
    private final String consumerName;
    private volatile boolean groupReady;

    public RedisStreamTransport(
            StringRedisTemplate redisTemplate,
            String streamKey,
            String group,
            String consumerName) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.streamKey = Objects.requireNonNull(streamKey, "streamKey");
        this.group = Objects.requireNonNull(group, "group");
        this.consumerName = Objects.requireNonNull(consumerName, "consumerName");
    }

    public boolean ensureGroup() {
        if (groupReady) {
            return true;
        }
        try {
            streams().createGroup(streamKey, ReadOffset.from("0-0"), group);
            groupReady = true;
            return true;
        } catch (RuntimeException exception) {
            if (groupExists()) {
                groupReady = true;
                return true;
            }
            return false;
        }
    }

    public List<MapRecord<String, String, String>> read(ReadOffset offset, int batchSize) {
        try {
            List<MapRecord<String, String, String>> records = streams().read(
                    Consumer.from(group, consumerName),
                    StreamReadOptions.empty().count(batchSize),
                    StreamOffset.create(streamKey, offset));
            return records == null ? List.of() : records;
        } catch (RuntimeException exception) {
            invalidateWhenGroupIsMissing(exception);
            throw exception;
        }
    }

    public PendingMessages pending(int batchSize) {
        try {
            return streams().pending(streamKey, group, Range.unbounded(), batchSize);
        } catch (RuntimeException exception) {
            invalidateWhenGroupIsMissing(exception);
            throw exception;
        }
    }

    public List<MapRecord<String, String, String>> reclaim(
            PendingMessages pending, Duration minIdle) {
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }
        List<RecordId> ids = new ArrayList<>();
        for (PendingMessage message : pending) {
            ids.add(message.getId());
        }
        return reclaim(ids, minIdle);
    }

    public List<MapRecord<String, String, String>> reclaim(
            List<RecordId> ids, Duration minIdle) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try {
            List<MapRecord<String, String, String>> reclaimed = streams().claim(
                    streamKey,
                    group,
                    consumerName,
                    minIdle,
                    ids.toArray(RecordId[]::new));
            return reclaimed == null ? List.of() : reclaimed;
        } catch (RuntimeException exception) {
            invalidateWhenGroupIsMissing(exception);
            throw exception;
        }
    }

    public Long acknowledge(RecordId recordId) {
        try {
            return streams().acknowledge(streamKey, group, recordId);
        } catch (RuntimeException exception) {
            invalidateWhenGroupIsMissing(exception);
            throw exception;
        }
    }

    public StreamOperations<String, String, String> streams() {
        return redisTemplate.opsForStream();
    }

    public String streamKey() {
        return streamKey;
    }

    public String group() {
        return group;
    }

    public String consumerName() {
        return consumerName;
    }

    private void invalidateWhenGroupIsMissing(RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("NOGROUP")) {
                groupReady = false;
                return;
            }
            current = current.getCause();
        }
    }

    private boolean groupExists() {
        try {
            StreamInfo.XInfoGroups groups = streams().groups(streamKey);
            return groups != null && groups.stream().anyMatch(info -> group.equals(info.groupName()));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
