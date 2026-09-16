package com.ulticode.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.LocalDateTime;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
