package com.ulticode.modules.user.entity;

import java.time.LocalDateTime;

/** User entity representing a platform user. */
public record User(
    String id,
    String username,
    String email,
    String password,
    String avatar,
    String bio,
    String role,
    Integer rating,
    LocalDateTime joinedAt,
    LocalDateTime updatedAt) {

  /** User roles in the system. */
  public enum Role {
    USER,
    ADMIN,
    SUPER_ADMIN
  }
}
