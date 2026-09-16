package com.ulticode.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.math.BigDecimal;

/**
 * Shared value-serialization policy for owner Redis configurations.
 *
 * <p>This class intentionally exposes factories rather than Spring beans. Owner
 * configurations retain control of their connection factories, TTLs, keys, and
 * bean names. The mapper settings are a cache byte-compatibility contract:
 * preserve field visibility, default typing, polymorphic type properties, and
 * ISO-8601 Java time values when changing this policy.
 *
 * <h2>Type allowlist for polymorphic deserialization</h2>
 *
 * <p>Default typing is required to round-trip the concrete DTOs these caches
 * store, and existing Redis bytes carry type ids written by the four
 * pre-unification owner configurations. The write side therefore stays a
 * default-typing mapper (the serialized bytes are unchanged), but the read side
 * is constrained by a {@link BasicPolymorphicTypeValidator} allowlist:
 * {@code com.ulticode.} (owner DTOs), {@code java.util.} (collection
 * containers), {@code java.time.} (JSR-310 values), and {@code java.lang.}
 * (boxed primitives, strings, enums). Any other subtype named in a payload is
 * rejected instead of being instantiated.
 *
 * <p>Values written through this policy before the allowlist landed still
 * deserialize as long as their concrete types are inside the allowlist. If a
 * legacy value names a type outside it (none are known today), the read fails
 * closed and the entry must be re-materialized by its owner rather than the
 * allowlist being widened without an owner review.
 */
public final class RedisValueSerializationPolicy {

    /**
     * Allowed packages for polymorphic deserialization of cached values. Keep
     * this list explicit: widening it is a security-relevant change.
     * {@code BigDecimal} is allowed as a single class because cached owner VOs
     * carry it (for example {@code ProblemVO.acceptanceRate} and
     * {@code ContestRankingVO.progress}); it is not part of any broader package
     * that would be allowed wholesale.
     */
    private static final PolymorphicTypeValidator POLYMORPHIC_TYPE_VALIDATOR =
            BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType("com.ulticode.")
                    .allowIfSubType("java.util.")
                    .allowIfSubType("java.time.")
                    .allowIfSubType("java.lang.")
                    .allowIfSubType(BigDecimal.class)
                    .build();

    private RedisValueSerializationPolicy() {
    }

    /**
     * Creates the mapper used by the shared Redis value serializer.
     *
     * @return a fresh mapper configured for the established Redis value format
     */
    public static ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                POLYMORPHIC_TYPE_VALIDATOR,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    /**
     * Creates the serializer used for Redis values and hash values.
     *
     * @return a serializer using {@link #objectMapper()}
     */
    public static GenericJackson2JsonRedisSerializer valueSerializer() {
        return new GenericJackson2JsonRedisSerializer(objectMapper());
    }
}
