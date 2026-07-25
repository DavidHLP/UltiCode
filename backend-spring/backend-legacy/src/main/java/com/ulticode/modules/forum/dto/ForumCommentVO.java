package com.ulticode.modules.forum.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Forum Comment View Object for API responses.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForumCommentVO {

    /**
     * Comment unique identifier
     */
    private String id;

    /**
     * ID of the post this comment belongs to
     */
    private String postId;

    /**
     * ID of the parent comment (for nested replies)
     */
    private String parentId;

    /**
     * ID of the user who created this comment
     */
    private String authorId;

    /**
     * Author username
     */
    private String authorUsername;

    /**
     * Author avatar
     */
    private String authorAvatar;

    /**
     * Comment body (plain text)
     */
    private String body;

    /**
     * Comment content in markdown format
     */
    private String markdown;

    /**
     * Record creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * When the comment was last edited
     */
    private LocalDateTime editedAt;

    /**
     * Whether the comment is pinned
     */
    private Boolean isPinned;

    /**
     * Whether the comment is locked
     */
    private Boolean isLocked;

    /**
     * Whether the comment is flagged
     */
    private Boolean isFlagged;

    /**
     * Reason for flagging
     */
    private String flaggedReason;

    /**
     * When the comment was flagged
     */
    private LocalDateTime flaggedAt;

    /**
     * Whether current user is the author
     */
    private Boolean isAuthor;

    /**
     * Number of replies
     */
    private Long replyCount;

    /**
     * Nested replies (for thread view)
     */
    private List<ForumCommentVO> replies;
}
