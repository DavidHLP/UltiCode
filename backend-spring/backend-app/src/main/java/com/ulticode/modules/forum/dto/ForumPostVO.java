package com.ulticode.modules.forum.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Forum Post View Object for API responses.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForumPostVO {

    /**
     * Post unique identifier
     */
    private String id;

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
     * ID of the user who created this post
     */
    private String userId;

    /**
     * Author username
     */
    private String authorUsername;

    /**
     * Author avatar
     */
    private String authorAvatar;

    /**
     * Permalink/slug for the post
     */
    private String permalink;

    /**
     * Post title
     */
    private String title;

    /**
     * Type of flair
     */
    private String flairType;

    /**
     * Label text for the flair
     */
    private String flairLabel;

    /**
     * Tags associated with this post
     */
    private List<String> tags;

    /**
     * Excerpt/summary of the post content
     */
    private String excerpt;

    /**
     * Media attachments
     */
    private Object media;

    /**
     * Vote state for current user
     */
    private String voteState;

    /**
     * Whether the post is saved/bookmarked
     */
    private Boolean isSaved;

    /**
     * Number of impressions/views
     */
    private Integer impressions;

    /**
     * Whether the post is pinned
     */
    private Boolean isPinned;

    /**
     * Whether the post is locked
     */
    private Boolean isLocked;

    /**
     * Post statistics
     */
    private Object stats;

    /**
     * Number of views
     */
    private Integer views;

    /**
     * Number of comments
     */
    private Long commentCount;

    /**
     * Whether the post is flagged
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
     * Record creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Whether current user is the author
     */
    private Boolean isAuthor;

    /**
     * Whether current user is a member of the community
     */
    private Boolean isMember;
}
