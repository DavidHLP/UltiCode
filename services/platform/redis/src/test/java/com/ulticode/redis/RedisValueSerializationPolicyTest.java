package com.ulticode.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.support.NullValue;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisValueSerializationPolicyTest {

    private static final LocalDateTime SAMPLE_TIME = LocalDateTime.of(2026, 8, 5, 10, 3, 48);

    @Test
    @DisplayName("shared value serializer round-trips a LocalDateTime-bearing value")
    void sharedValueSerializerRoundTripsLocalDateTime() {
        GenericJackson2JsonRedisSerializer serializer =
                RedisValueSerializationPolicy.valueSerializer();
        SampleValue original = new SampleValue("PROCESSING", SAMPLE_TIME);

        byte[] bytes = serializer.serialize(original);
        Object deserialized = serializer.deserialize(bytes);

        assertEquals(original, deserialized,
                "the shared serializer must preserve the value type and LocalDateTime");
    }

    @Test
    @DisplayName("shared value serializer round-trips an allowlisted collection container")
    void sharedValueSerializerRoundTripsCollectionContainer() {
        GenericJackson2JsonRedisSerializer serializer =
                RedisValueSerializationPolicy.valueSerializer();
        // ArrayList is non-final, so default typing writes its type id; the
        // read side must accept java.util containers from the allowlist.
        ArrayList<SampleValue> original = new ArrayList<>(
                List.of(new SampleValue("PROCESSING", SAMPLE_TIME)));

        byte[] bytes = serializer.serialize(original);
        Object deserialized = serializer.deserialize(bytes);

        assertEquals(original, deserialized,
                "java.util collection containers must stay inside the allowlist");
    }

    @Test
    @DisplayName("polymorphic deserialization rejects a subtype outside the allowlist")
    void polymorphicDeserializationRejectsDisallowedSubtype() {
        GenericJackson2JsonRedisSerializer serializer =
                RedisValueSerializationPolicy.valueSerializer();
        // A default-typed java.io.File value (classic payload shape). The
        // allowlist covers com.ulticode./java.util./java.time./java.lang. only,
        // so this type id must be rejected before any construction is attempted.
        byte[] payload = "[\"java.io.File\",\"/etc/passwd\"]".getBytes(StandardCharsets.UTF_8);

        Exception failure = assertThrows(Exception.class, () -> serializer.deserialize(payload),
                "a subtype outside the allowlist must not deserialize");

        assertTrue(describe(failure).contains("java.io.File"),
                "the rejection must name the disallowed type: " + describe(failure));
    }

    private static String describe(Throwable throwable) {
        StringBuilder description = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            description.append(current).append(' ');
        }
        return description.toString();
    }

    @Test
    @DisplayName("shared value serializer round-trips cached DTO shapes carrying BigDecimal")
    void sharedValueSerializerRoundTripsBigDecimal() {
        GenericJackson2JsonRedisSerializer serializer =
                RedisValueSerializationPolicy.valueSerializer();
        RatedValue original = new RatedValue("problem-1", new BigDecimal("0.8543"), SAMPLE_TIME);

        byte[] bytes = serializer.serialize(original);
        Object deserialized = serializer.deserialize(bytes);

        assertEquals(original, deserialized,
                "cached owner VOs carry BigDecimal fields (ProblemVO.acceptanceRate, ContestRankingVO.progress)");
    }

    @Test
    @DisplayName("cache null sentinel is not silently stored by the shared policy")
    void cacheNullSentinelIsNotSilentlyStored() {
        GenericJackson2JsonRedisSerializer serializer =
                RedisValueSerializationPolicy.valueSerializer();
        // Spring's cache null sentinel is only serializable after
        // GenericJackson2JsonRedisSerializer.registerNullValueSerializer is
        // registered on the mapper; without it the write fails loudly. The
        // allowlist constrains reads only, so this matches the pre-allowlist
        // behavior exactly. If null caching is ever required, register the
        // sentinel serializer and allow exactly NullValue.class in the allowlist.
        Exception failure = assertThrows(Exception.class,
                () -> serializer.serialize(NullValue.INSTANCE),
                "the null sentinel must fail loudly instead of storing a wrong value");

        assertTrue(describe(failure).contains("NullValue"),
                "the failure must name the sentinel: " + describe(failure));
    }

    @Test
    @DisplayName("legacy no-arg serializer fails on LocalDateTime")
    void legacySerializerFailsOnLocalDateTime() {
        GenericJackson2JsonRedisSerializer legacy = new GenericJackson2JsonRedisSerializer();

        assertThrows(Exception.class,
                () -> legacy.serialize(new SampleValue("PROCESSING", SAMPLE_TIME)),
                "the no-arg serializer must remain the documented incompatible legacy path");
    }

    public static class SampleValue {
        private String status;
        private LocalDateTime occurredAt;

        public SampleValue() {
        }

        private SampleValue(String status, LocalDateTime occurredAt) {
            this.status = status;
            this.occurredAt = occurredAt;
        }

        public String getStatus() {
            return status;
        }

        public LocalDateTime getOccurredAt() {
            return occurredAt;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof SampleValue that)) {
                return false;
            }
            return Objects.equals(status, that.status)
                    && Objects.equals(occurredAt, that.occurredAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, occurredAt);
        }
    }

    public static class RatedValue {
        private String entityId;
        private BigDecimal rate;
        private LocalDateTime updatedAt;

        public RatedValue() {
        }

        private RatedValue(String entityId, BigDecimal rate, LocalDateTime updatedAt) {
            this.entityId = entityId;
            this.rate = rate;
            this.updatedAt = updatedAt;
        }

        public String getEntityId() {
            return entityId;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RatedValue that)) {
                return false;
            }
            return Objects.equals(entityId, that.entityId)
                    && Objects.equals(rate, that.rate)
                    && Objects.equals(updatedAt, that.updatedAt);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityId, rate, updatedAt);
        }
    }
}
