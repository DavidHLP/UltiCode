package com.ulticode.auth.refreshtoken.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Refresh Token entity.
 * Stores refresh tokens in the refresh_tokens table for database-backed token management.
 */
@Data
@TableName("refresh_tokens")
public class RefreshToken {

    /**
     * Unique token identifier (UUID)
     */
    @TableId(type = IdType.INPUT)
    private String id;

    /**
     * User ID associated with this token
     */
    private String userId;

    /**
     * SHA-256 hash of the token for secure lookup
     */
    private String tokenHash;

    /**
     * Token expiration timestamp
     */
    private LocalDateTime expiresAt;

    /**
     * Token creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when token was rotated
     */
    private LocalDateTime rotatedAt;

    /**
     * Whether the token has been revoked
     */
    @TableField("is_revoked")
    private Boolean isRevoked;

    /**
     * AUTH-COMP-005: opaque id grouping all rotation-chain siblings from one login.
     * NULL for legacy tokens that predate family tracking.
     */
    private String familyId;

    /**
     * AUTH-COMP-005: id of the sibling row that replaced this one (rotation forward link).
     */
    private String replacedByTokenId;

    /**
     * AUTH-COMP-005: id of the sibling row that preceded this one (rotation backward link).
     */
    private String previousTokenId;
}
