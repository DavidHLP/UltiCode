package com.ulticode.modules.auth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT utilities for token generation and validation. */
@Component
public class JwtUtils {

  private final SecretKey secretKey;
  private final long expirationMs;

  public JwtUtils(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token.expiration:604800000}") long expirationMs) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMs = expirationMs;
  }

  /**
   * Generate a JWT token for a user.
   *
   * @param userId the user ID
   * @param username the username
   * @param role the user role
   * @return the generated JWT token
   */
  public String generateToken(String userId, String username, String role) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + expirationMs);

    return Jwts.builder()
        .subject(userId)
        .claim("username", username)
        .claim("role", role)
        .issuedAt(now)
        .expiration(expiryDate)
        .signWith(secretKey)
        .compact();
  }

  /**
   * Validate and parse a JWT token.
   *
   * @param token the JWT token
   * @return Optional containing claims if valid, empty otherwise
   */
  public Optional<Claims> validateToken(String token) {
    try {
      Claims claims =
          Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
      return Optional.of(claims);
    } catch (SignatureException
        | MalformedJwtException
        | ExpiredJwtException
        | UnsupportedJwtException
        | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /**
   * Extract user ID from token.
   *
   * @param token the JWT token
   * @return Optional containing user ID if valid
   */
  public Optional<String> extractUserId(String token) {
    return validateToken(token).map(Claims::getSubject);
  }

  /**
   * Extract username from token.
   *
   * @param token the JWT token
   * @return Optional containing username if valid
   */
  public Optional<String> extractUsername(String token) {
    return validateToken(token).map(claims -> claims.get("username", String.class));
  }

  /**
   * Extract user role from token.
   *
   * @param token the JWT token
   * @return Optional containing role if valid
   */
  public Optional<String> extractRole(String token) {
    return validateToken(token).map(claims -> claims.get("role", String.class));
  }

  /**
   * Extract all claims from token.
   *
   * @param token the JWT token
   * @return JwtPayload containing all token claims if valid
   */
  public Optional<JwtPayload> extractPayload(String token) {
    return validateToken(token)
        .map(
            claims ->
                new JwtPayload(
                    claims.getSubject(),
                    claims.get("username", String.class),
                    claims.get("role", String.class)));
  }

  /** JWT payload data. */
  public record JwtPayload(String userId, String username, String role) {}
}
