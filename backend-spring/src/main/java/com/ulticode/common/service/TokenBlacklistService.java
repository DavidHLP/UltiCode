package com.ulticode.common.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for token blacklist operations using Redis.
 *
 * <p>Provides token blacklist management with SHA-256 hashing for secure storage.
 */
@Service
public class TokenBlacklistService {

  private final StringRedisTemplate redisTemplate;

  /** Default TTL for blacklisted tokens (7 days in seconds). */
  private static final long DEFAULT_TOKEN_TTL = 7 * 24 * 60 * 60;

  /** Key prefix for token blacklist. */
  private static final String TOKEN_BLACKLIST_PREFIX = "blacklist:token:";

  public TokenBlacklistService(StringRedisTemplate redisTemplate) {
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
}
