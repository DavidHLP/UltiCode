package com.ulticode.app.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ADMIN-007: flat, entity-free projection of a {@code forum_posts} row
 * for the Admin forum list / detail reads.
 *
 * <p>The {@code backend-app} provider fills every field except
 * {@link #upvotes} / {@link #downvotes}, which are owned by the vote
 * module and merged in by the Admin-side consumer adapter via
 * {@link com.ulticode.app.api.service.ForumPostVoteCountReadPort}.
 * {@link #content} is populated only on the single-detail read (the
 * forum entity has no separate content column; the detail view mirrors
 * the excerpt, matching the pre-migration VO behavior).
 */
@Data
public class AdminForumPostRowDTO implements Serializable {
    private static final long serialVersionUID = 1L;


    /** Post unique identifier. */
    private String id;

    /** Post title. */
    private String title;

    /** Excerpt / summary of the post content. */
    private String excerpt;

    /** Full content (detail view only; mirrors {@link #excerpt}). */
    private String content;

    /** ID of the user who created this post. */
    private String userId;

    /** ID of the community this post belongs to. */
    private String communityId;

    /** Community display name (denormalized for the admin list). */
    private String communityName;

    /** Community slug (denormalized for the admin list). */
    private String communitySlug;

    /** Number of impressions / views. */
    private Integer views;

    /** Number of non-deleted comments on the post. */
    private Integer commentCount;

    /** Number of upvotes (VOTE_UP on FORUM_POST) — filled by the Admin consumer. */
    private Integer upvotes;

    /** Number of downvotes (VOTE_DOWN on FORUM_POST) — filled by the Admin consumer. */
    private Integer downvotes;

    /** Whether the post is pinned. */
    private Boolean isPinned;

    /** Whether the post is locked (no comments allowed). */
    private Boolean isLocked;

    /** Whether the post is flagged for review. */
    private Boolean isFlagged;

    /** Reason for flagging. */
    private String flaggedReason;

    /** When the post was flagged. */
    private LocalDateTime flaggedAt;

    /** Whether the post is soft deleted. */
    private Boolean isDeleted;

    /** When the post was deleted. */
    private LocalDateTime deletedAt;

    /** When the post was created. */
    private LocalDateTime createdAt;

    /** When the post was last updated. */
    private LocalDateTime updatedAt;
}
