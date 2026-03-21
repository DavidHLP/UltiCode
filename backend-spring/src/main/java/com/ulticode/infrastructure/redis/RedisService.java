package com.ulticode.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis service wrapper providing common Redis operations.
 * Encapsulates RedisTemplate operations with simplified API and error handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    // ==================== String Operations ====================

    /**
     * Set a key-value pair.
     *
     * @param key   the key
     * @param value the value
     */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("Redis set error for key: {}", key, e);
            throw new RuntimeException("Failed to set value in Redis", e);
        }
    }

    /**
     * Set a key-value pair with TTL.
     *
     * @param key     the key
     * @param value   the value
     * @param timeout the timeout duration
     * @param unit    the time unit
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.error("Redis set with TTL error for key: {}", key, e);
            throw new RuntimeException("Failed to set value with TTL in Redis", e);
        }
    }

    /**
     * Set a key-value pair with TTL in seconds.
     *
     * @param key     the key
     * @param value   the value
     * @param seconds the TTL in seconds
     */
    public void setEx(String key, Object value, long seconds) {
        set(key, value, seconds, TimeUnit.SECONDS);
    }

    /**
     * Get value by key.
     *
     * @param key the key
     * @return the value, or null if key doesn't exist
     */
    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Redis get error for key: {}", key, e);
            return null;
        }
    }

    /**
     * Get value by key and cast to specified type.
     *
     * @param key   the key
     * @param clazz the target type
     * @param <T>   the type parameter
     * @return the value cast to the specified type, or null if key doesn't exist or cast fails
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null && clazz.isInstance(value)) {
                return (T) value;
            }
            return null;
        } catch (Exception e) {
            log.error("Redis get error for key: {}", key, e);
            return null;
        }
    }

    /**
     * Delete one or more keys.
     *
     * @param keys the keys to delete
     * @return the number of keys deleted
     */
    public Long delete(String... keys) {
        try {
            if (keys == null || keys.length == 0) {
                return 0L;
            }
            if (keys.length == 1) {
                return redisTemplate.delete(keys[0]) ? 1L : 0L;
            }
            return redisTemplate.delete(java.util.Arrays.asList(keys));
        } catch (Exception e) {
            log.error("Redis delete error for keys: {}", (Object) keys, e);
            return 0L;
        }
    }

    /**
     * Delete a collection of keys.
     *
     * @param keys the collection of keys to delete
     * @return the number of keys deleted
     */
    public Long delete(Collection<String> keys) {
        try {
            if (keys == null || keys.isEmpty()) {
                return 0L;
            }
            return redisTemplate.delete(keys);
        } catch (Exception e) {
            log.error("Redis delete error for keys: {}", keys, e);
            return 0L;
        }
    }

    /**
     * Check if a key exists.
     *
     * @param key the key
     * @return true if the key exists
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Redis hasKey error for key: {}", key, e);
            return false;
        }
    }

    /**
     * Set expiration time for a key.
     *
     * @param key     the key
     * @param timeout the timeout duration
     * @param unit    the time unit
     * @return true if the expiration was set
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            log.error("Redis expire error for key: {}", key, e);
            return false;
        }
    }

    /**
     * Get the remaining TTL of a key in seconds.
     *
     * @param key the key
     * @return the TTL in seconds, -1 if no expiration, -2 if key doesn't exist
     */
    public Long getExpire(String key) {
        try {
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Redis getExpire error for key: {}", key, e);
            return -2L;
        }
    }

    /**
     * Increment a value by 1.
     *
     * @param key the key
     * @return the new value after increment
     */
    public Long increment(String key) {
        try {
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.error("Redis increment error for key: {}", key, e);
            throw new RuntimeException("Failed to increment value in Redis", e);
        }
    }

    /**
     * Increment a value by delta.
     *
     * @param key   the key
     * @param delta the increment amount
     * @return the new value after increment
     */
    public Long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            log.error("Redis increment error for key: {} with delta: {}", key, delta, e);
            throw new RuntimeException("Failed to increment value in Redis", e);
        }
    }

    /**
     * Decrement a value by 1.
     *
     * @param key the key
     * @return the new value after decrement
     */
    public Long decrement(String key) {
        try {
            return redisTemplate.opsForValue().decrement(key);
        } catch (Exception e) {
            log.error("Redis decrement error for key: {}", key, e);
            throw new RuntimeException("Failed to decrement value in Redis", e);
        }
    }

    // ==================== Hash Operations ====================

    /**
     * Set a hash field.
     *
     * @param key     the hash key
     * @param field   the field
     * @param value   the value
     */
    public void hSet(String key, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
        } catch (Exception e) {
            log.error("Redis hSet error for key: {}, field: {}", key, field, e);
            throw new RuntimeException("Failed to set hash field in Redis", e);
        }
    }

    /**
     * Set multiple hash fields.
     *
     * @param key the hash key
     * @param map the field-value map
     */
    public void hSetAll(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
        } catch (Exception e) {
            log.error("Redis hSetAll error for key: {}", key, e);
            throw new RuntimeException("Failed to set hash fields in Redis", e);
        }
    }

    /**
     * Get a hash field value.
     *
     * @param key   the hash key
     * @param field the field
     * @return the value, or null if field doesn't exist
     */
    public Object hGet(String key, String field) {
        try {
            return redisTemplate.opsForHash().get(key, field);
        } catch (Exception e) {
            log.error("Redis hGet error for key: {}, field: {}", key, field, e);
            return null;
        }
    }

    /**
     * Get all hash fields and values.
     *
     * @param key the hash key
     * @return the map of all fields and values
     */
    public Map<Object, Object> hGetAll(String key) {
        try {
            return redisTemplate.opsForHash().entries(key);
        } catch (Exception e) {
            log.error("Redis hGetAll error for key: {}", key, e);
            return Map.of();
        }
    }

    /**
     * Delete hash fields.
     *
     * @param key    the hash key
     * @param fields the fields to delete
     * @return the number of fields deleted
     */
    public Long hDelete(String key, Object... fields) {
        try {
            return redisTemplate.opsForHash().delete(key, fields);
        } catch (Exception e) {
            log.error("Redis hDelete error for key: {}", key, e);
            return 0L;
        }
    }

    /**
     * Check if a hash field exists.
     *
     * @param key   the hash key
     * @param field the field
     * @return true if the field exists
     */
    public boolean hHasKey(String key, String field) {
        try {
            return redisTemplate.opsForHash().hasKey(key, field);
        } catch (Exception e) {
            log.error("Redis hHasKey error for key: {}, field: {}", key, field, e);
            return false;
        }
    }

    /**
     * Increment a hash field value.
     *
     * @param key   the hash key
     * @param field the field
     * @param delta the increment amount
     * @return the new value after increment
     */
    public Long hIncrement(String key, String field, long delta) {
        try {
            return redisTemplate.opsForHash().increment(key, field, delta);
        } catch (Exception e) {
            log.error("Redis hIncrement error for key: {}, field: {}", key, field, e);
            throw new RuntimeException("Failed to increment hash field in Redis", e);
        }
    }

    /**
     * Get the number of hash fields.
     *
     * @param key the hash key
     * @return the number of fields
     */
    public Long hSize(String key) {
        try {
            return redisTemplate.opsForHash().size(key);
        } catch (Exception e) {
            log.error("Redis hSize error for key: {}", key, e);
            return 0L;
        }
    }

    // ==================== Set Operations ====================

    /**
     * Add members to a set.
     *
     * @param key     the set key
     * @param values  the values to add
     * @return the number of values added
     */
    public Long sAdd(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            log.error("Redis sAdd error for key: {}", key, e);
            return 0L;
        }
    }

    /**
     * Remove members from a set.
     *
     * @param key     the set key
     * @param values  the values to remove
     * @return the number of values removed
     */
    public Long sRemove(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().remove(key, values);
        } catch (Exception e) {
            log.error("Redis sRemove error for key: {}", key, e);
            return 0L;
        }
    }

    /**
     * Check if a value is a member of a set.
     *
     * @param key   the set key
     * @param value the value to check
     * @return true if the value is a member
     */
    public boolean sIsMember(String key, Object value) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
        } catch (Exception e) {
            log.error("Redis sIsMember error for key: {}", key, e);
            return false;
        }
    }

    /**
     * Get the size of a set.
     *
     * @param key the set key
     * @return the size of the set
     */
    public Long sSize(String key) {
        try {
            return redisTemplate.opsForSet().size(key);
        } catch (Exception e) {
            log.error("Redis sSize error for key: {}", key, e);
            return 0L;
        }
    }

    // ==================== List Operations ====================

    /**
     * Push a value to the left of a list.
     *
     * @param key   the list key
     * @param value the value
     * @return the length of the list after push
     */
    public Long lPush(String key, Object value) {
        try {
            return redisTemplate.opsForList().leftPush(key, value);
        } catch (Exception e) {
            log.error("Redis lPush error for key: {}", key, e);
            return 0L;
        }
    }

    /**
     * Push a value to the right of a list.
     *
     * @param key   the list key
     * @param value the value
     * @return the length of the list after push
     */
    public Long rPush(String key, Object value) {
        try {
            return redisTemplate.opsForList().rightPush(key, value);
        } catch (Exception e) {
            log.error("Redis rPush error for key: {}", key, e);
            return 0L;
        }
    }

    /**
     * Pop a value from the left of a list.
     *
     * @param key the list key
     * @return the popped value, or null if list is empty
     */
    public Object lPop(String key) {
        try {
            return redisTemplate.opsForList().leftPop(key);
        } catch (Exception e) {
            log.error("Redis lPop error for key: {}", key, e);
            return null;
        }
    }

    /**
     * Pop a value from the right of a list.
     *
     * @param key the list key
     * @return the popped value, or null if list is empty
     */
    public Object rPop(String key) {
        try {
            return redisTemplate.opsForList().rightPop(key);
        } catch (Exception e) {
            log.error("Redis rPop error for key: {}", key, e);
            return null;
        }
    }

    /**
     * Get the length of a list.
     *
     * @param key the list key
     * @return the length of the list
     */
    public Long lSize(String key) {
        try {
            return redisTemplate.opsForList().size(key);
        } catch (Exception e) {
            log.error("Redis lSize error for key: {}", key, e);
            return 0L;
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Get keys matching a pattern.
     * Note: Use with caution on large datasets.
     *
     * @param pattern the pattern to match
     * @return the set of matching keys
     */
    public java.util.Set<String> keys(String pattern) {
        try {
            return redisTemplate.keys(pattern);
        } catch (Exception e) {
            log.error("Redis keys error for pattern: {}", pattern, e);
            return java.util.Set.of();
        }
    }
}
