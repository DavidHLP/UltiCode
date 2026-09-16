package com.ulticode.app.config;

import com.ulticode.redis.RedisValueSerializationPolicy;
import com.ulticode.modules.queue.constants.QueueConstants;
import com.ulticode.modules.queue.dto.JobStatusDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression for the {@code System Error — Judge queue unavailable} outage.
 *
 * <p>Root cause: the legacy no-arg {@code GenericJackson2JsonRedisSerializer}
 * omitted {@code JavaTimeModule}. When {@code QueueServiceImpl#saveJobStatus}
 * wrote a {@link JobStatusDTO} carrying a non-null {@code LocalDateTime
 * createdAt}, serialization threw {@code SerializationException}, which the
 * submission intake catch block translated to {@code SYSTEM_ERROR} + "Judge
 * queue unavailable". Existing {@code QueueServiceTest} mocked the
 * {@code RedisTemplate} with deep stubs, so the real serialization path was
 * never exercised and the bug shipped.
 *
 * <p>This test locks the shared {@link RedisValueSerializationPolicy} at the
 * serialization seam itself (no Spring context, no Redis): the configured
 * serializer must round-trip a {@link JobStatusDTO} with populated
 * {@link LocalDateTime} fields, and the legacy no-arg serializer must
 * demonstrably fail the same input.
 */
class AppRedisTemplateConfigSerializationTest {

    /** A status DTO whose every date/time field is populated. */
    private static JobStatusDTO sampleStatus() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 5, 10, 3, 48);
        return JobStatusDTO.builder()
                .jobId("88451e8c-ede5-4d15-98ac-9522cc7b77fb")
                .jobType("JUDGE")
                .queueName(QueueConstants.JUDGE_QUEUE)
                .status(QueueConstants.JobStatus.PROCESSING)
                .priority(QueueConstants.Priority.HIGH)
                .progress(0)
                .attempts(0)
                .maxRetries(QueueConstants.DEFAULT_MAX_RETRIES)
                .userId("aa80236d-89ab-11f1-ae1f-5ef613d60703")
                .createdAt(now)
                .startedAt(now)
                .completedAt(now)
                .durationMs(0L)
                .build();
    }

    /**
     * The shared value-serialization policy used by app, admin, and judge
     * owner Redis configurations.
     */
    private static GenericJackson2JsonRedisSerializer configuredSerializer() {
        return RedisValueSerializationPolicy.valueSerializer();
    }

    @Test
    @DisplayName("configured serializer round-trips JobStatusDTO with LocalDateTime fields")
    void configuredSerializerRoundTripsLocalDateTime() {
        GenericJackson2JsonRedisSerializer serializer = configuredSerializer();
        JobStatusDTO original = sampleStatus();

        byte[] bytes = serializer.serialize(original);
        Object deserialized = serializer.deserialize(bytes);

        assertEquals(original, deserialized,
                "JobStatusDTO with LocalDateTime must round-trip through the configured serializer");
    }

    @Test
    @DisplayName("legacy no-arg serializer fails on LocalDateTime — proves the regression target")
    void legacySerializerFailsOnLocalDateTime() {
        GenericJackson2JsonRedisSerializer legacy = new GenericJackson2JsonRedisSerializer();
        JobStatusDTO status = sampleStatus();

        // The no-arg constructor omits JavaTimeModule, so writing any
        // LocalDateTime-bearing DTO reproduces the original outage.
        assertThrows(Exception.class, () -> legacy.serialize(status),
                "the legacy no-arg serializer must fail on LocalDateTime (documents the root cause)");
    }
}
