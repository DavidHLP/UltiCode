package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin Forum Post View Object for admin panel API responses.
 * Contains all post fields needed for admin management.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminForumPostVO {

    /**
     * Post unique identifier
     */
    private String id;

    /**
     * Post title
     */
    private String title;

    /**
     * Excerpt/summary of the post content
     */
    private String excerpt;

    /**
     * Full content (for detail view)
     */
    private String content;

    /**
     * ID of the user who created this post
     */
    private String userId;

    /**
     * Author username
     */
    private String username;

    /**
     * Author avatar URL
     */
    private String avatar;

    /**
     * ID of the community this post belongs to
     */
    private String communityId;

    /**
     * Community name
     */
    private String communityName;

    /**
     * Community slug
     */
    private String communitySlug;

    /**
     * Number of views
     */
    private Integer viewCount;

    /**
     * Number of comments
     */
    private Integer commentCount;

    /**
     * Number of upvotes
     */
    private Integer upvotes;

    /**
     * Number of downvotes
     */
    private Integer downvotes;

    /**
     * Whether the post is pinned
     */
    private Boolean isPinned;

    /**
     * Whether the post is locked (no comments allowed)
     */
    private Boolean isLocked;

    /**
     * Whether the post is flagged for review
     */
    private Boolean isFlagged;

    /**
     * Reason for flagging
     */
    private String flaggedReason;

    /**
     * When the post was flagged
     */
    private LocalDateTime flaggedAt;

    /**
     * Whether the post is soft deleted
     */
    private Boolean isDeleted;

    /**
     * When the post was deleted
     */
    private LocalDateTime deletedAt;

    /**
     * When the post was created
     */
    private LocalDateTime createdAt;

    /**
     * When the post was last updated
     */
    private LocalDateTime updatedAt;
}
