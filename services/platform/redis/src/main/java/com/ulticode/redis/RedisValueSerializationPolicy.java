package com.ulticode.redis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

/**
 * Shared value-serialization policy for owner Redis configurations.
 *
 * <p>This class intentionally exposes factories rather than Spring beans. Owner
 * configurations retain control of their connection factories, TTLs, keys, and
 * bean names. The mapper settings are a cache byte-compatibility contract:
 * preserve field visibility, default typing, polymorphic type properties, and
 * ISO-8601 Java time values when changing this policy.
 *
 * <h2>Security exception: permissive polymorphic typing</h2>
 *
 * <p>{@link LaissezFaireSubTypeValidator} with {@code NON_FINAL} default typing
 * is deliberately retained even though permissive polymorphic JSON typing is
 * otherwise disallowed by the repository security rules. The exception exists
 * because these bytes are already stored in Redis by the four pre-unification
 * owner configurations; replacing them with an allowlist-based validator would
 * change the wire format and break every cached value on upgrade.
 *
 * <p>The risk is bounded to the Redis keys written through this policy: Redis
 * is an internal store that is not attacker-writable in the supported
 * topologies, and values are serialized only by this process family. The
 * upgrade path, when a breaking release is acceptable, is an explicit
 * polymorphic type allowlist plus a cache flush (delete the affected keys)
 * rather than a silent policy change.
 */
public final class RedisValueSerializationPolicy {

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
                LaissezFaireSubTypeValidator.instance,
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
