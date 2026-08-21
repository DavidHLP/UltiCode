package com.ulticode.modules.queue.migration;

import com.ulticode.submission.api.queue.JudgeStreamKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RStream;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.api.stream.StreamMessageId;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.Kryo5Codec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.util.Map;

/**
 * One-shot drain of the pre-extraction {@code judge:stream} into the
 * {@code judge:{judge-stream}:stream} key (CR P1-3).
 *
 * <p>The extraction renamed the production stream key and the dedup prefix.
 * On a rolling upgrade, entries already queued (or sitting in the old
 * consumer group's PEL) would never be read by the new worker and rows
 * already marked SENT would not be dispatched again. This component runs
 * once at startup on the judge role only, moves every remaining entry to
 * the current stream (using the write codec of the new adapter), and
 * deletes the legacy key.
 *
 * <p>Legacy entries were written through {@code RStream.add} with the
 * default Kryo5 client codec, so they are read back with
 * {@link Kryo5Codec}; the current stream and scripts use
 * {@link StringCodec} (see {@code RedissonStreamsJudgeQueueAdapter}'s codec
 * contract), so the drain writes plain-text fields.
 *
 * <p>Idempotency: a one-shot SETNX lock prevents concurrent judge
 * instances from double-draining, and a non-empty current stream aborts
 * the migration. Each migrated batch is {@code XDEL}'d from the legacy key
 * so a crash mid-drain resumes without duplicates.
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.features.judge-queue.use-port",
        havingValue = "true")
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.role:api}' == 'judge'")
public class JudgeStreamLegacyMigration {

    private static final int BATCH_SIZE = 500;

    private final RedissonClient redissonClient;

    @EventListener(ApplicationReadyEvent.class)
    public void migrateLegacyStream() {
        String legacyKey = JudgeStreamKeys.LEGACY_JUDGE_STREAM_KEY;
        RStream<String, String> legacy =
                redissonClient.getStream(legacyKey, new Kryo5Codec());
        if (!legacy.isExists()) {
            return;
        }
        RBucket<String> migrationLock = redissonClient.getBucket(
                JudgeStreamKeys.JUDGE_STREAM_MIGRATION_LOCK_KEY, StringCodec.INSTANCE);
        if (!migrationLock.trySet("1", 10, java.util.concurrent.TimeUnit.MINUTES)) {
            log.info("Legacy judge stream migration already in progress by another instance");
            return;
        }
        try {
            RStream<String, String> current = redissonClient.getStream(
                    JudgeStreamKeys.JUDGE_STREAM_KEY, StringCodec.INSTANCE);
            if (current.isExists() && current.size() > 0) {
                log.warn("Skipping legacy judge stream migration: current stream already has entries");
                return;
            }
            long migrated = 0;
            while (true) {
                Map<StreamMessageId, Map<String, String>> batch = legacy.range(
                        BATCH_SIZE, StreamMessageId.MIN, StreamMessageId.MAX);
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (Map.Entry<StreamMessageId, Map<String, String>> entry : batch.entrySet()) {
                    current.add(StreamAddArgs.entries(entry.getValue()));
                    migrated++;
                }
                legacy.remove(batch.keySet().toArray(new StreamMessageId[0]));
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }
            redissonClient.getKeys().delete(legacyKey);
            log.info("Legacy judge stream migration complete: moved {} entry(ies) from {} to {}",
                    migrated, legacyKey, JudgeStreamKeys.JUDGE_STREAM_KEY);
        } finally {
            redissonClient.getKeys().delete(JudgeStreamKeys.JUDGE_STREAM_MIGRATION_LOCK_KEY);
        }
    }
}
