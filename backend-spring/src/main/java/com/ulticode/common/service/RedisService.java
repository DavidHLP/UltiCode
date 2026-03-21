package com.ulticode.common.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis service for caching and token blacklist operations.
 *
 * <p>Provides common Redis operations including:
 *
 * <ul>
 *   <li>Token blacklist management
 *   <li>Generic caching with TTL
 *   <li>Cache invalidation
 * </ul>
 */
@Service
public class RedisService {

  private final StringRedisTemplate redisTemplate;

  /** Default TTL for blacklisted tokens (7 days in seconds). */
  private static final long DEFAULT_TOKEN_TTL = 7 * 24 * 60 * 60;

  /** Key prefix for token blacklist. */
  private static final String TOKEN_BLACKLIST_PREFIX = "blacklist:token:";

  /** Key prefix for general cache. */
  private static final String CACHE_PREFIX = "cache:";

  public RedisService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  // ==================== Token Blacklist Operations ====================

  /**
   * Add a token to the blacklist.
   *
   * @param token the JWT token to blacklist
   */
  public void blacklistToken(String token) {
    blacklistToken(token, DEFAULT_TOKEN_TTL);
  }

  /**
   * Add a token to the blacklist with custom TTL.
   *
   * @param token the JWT token to blacklist
   * @param ttlSeconds time to live in seconds
   */
  public void blacklistToken(String token, long ttlSeconds) {
    String key = getBlacklistKey(token);
    redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds));
  }

  /**
   * Check if a token is blacklisted.
   *
   * @param token the JWT token to check
   * @return true if the token is blacklisted
   */
  public boolean isTokenBlacklisted(String token) {
    String key = getBlacklistKey(token);
    return Boolean.TRUE.equals(redisTemplate.hasKey(key));
  }

  /**
   * Remove a token from the blacklist.
   *
   * @param token the JWT token to remove
   */
  public void removeFromBlacklist(String token) {
    String key = getBlacklistKey(token);
    redisTemplate.delete(key);
  }

  /**
   * Get the Redis key for a blacklisted token. Uses SHA-256 hash to avoid storing raw token.
   *
   * @param token the JWT token
   * @return the Redis key
   */
  private String getBlacklistKey(String token) {
    return TOKEN_BLACKLIST_PREFIX + hashToken(token);
  }

  /**
   * Hash a token using SHA-256.
   *
   * @param token the token to hash
   * @return hex-encoded hash
   */
  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is always available in Java
      throw new RuntimeException("SHA-256 algorithm not available", e);
    }
  }

  // ==================== Generic Cache Operations ====================

  /**
   * Set a cache value with TTL.
   *
   * @param key the cache key
   * @param value the value to cache
   * @param ttlSeconds time to live in seconds
   */
  public void set(String key, String value, long ttlSeconds) {
    String fullKey = CACHE_PREFIX + key;
    redisTemplate.opsForValue().set(fullKey, value, Duration.ofSeconds(ttlSeconds));
  }

  /**
   * Get a cached value.
   *
   * @param key the cache key
   * @return Optional containing the value if present
   */
  public Optional<String> get(String key) {
    String fullKey = CACHE_PREFIX + key;
    return Optional.ofNullable(redisTemplate.opsForValue().get(fullKey));
  }

  /**
   * Delete a cached value.
   *
   * @param key the cache key
   * @return true if the key was deleted
   */
  public boolean delete(String key) {
    String fullKey = CACHE_PREFIX + key;
    return Boolean.TRUE.equals(redisTemplate.delete(fullKey));
  }

  /**
   * Check if a key exists.
   *
   * @param key the cache key
   * @return true if the key exists
   */
  public boolean exists(String key) {
    String fullKey = CACHE_PREFIX + key;
    return Boolean.TRUE.equals(redisTemplate.hasKey(fullKey));
  }

  /**
   * Set expiration on a key.
   *
   * @param key the cache key
   * @param ttlSeconds time to live in seconds
   * @return true if expiration was set
   */
  public boolean expire(String key, long ttlSeconds) {
    String fullKey = CACHE_PREFIX + key;
    return Boolean.TRUE.equals(redisTemplate.expire(fullKey, Duration.ofSeconds(ttlSeconds)));
  }
}
