package com.ulticode.modules.solution.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Solution View Object for API responses.
 * Contains all fields needed for the frontend.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SolutionVO {

    /**
     * Solution unique identifier
     */
    private String id;

    /**
     * Problem ID this solution belongs to
     */
    private Long problemId;

    /**
     * User ID who created this solution
     */
    private String userId;

    /**
     * Author display name
     */
    private String authorName;

    /**
     * Stable author username used by public profile routes
     */
    private String authorUsername;

    /**
     * Author avatar
     */
    private String authorAvatar;

    /**
     * Solution title
     */
    private String title;

    /**
     * Solution content (markdown)
     */
    private String content;

    /**
     * Summary/excerpt of the solution
     */
    private String summary;

    /**
     * Programming language for this solution
     */
    private String language;

    /**
     * Tags associated with this solution (list of strings)
     */
    private List<String> tags;

    /**
     * Number of views
     */
    private Integer views;

    /**
     * Number of upvotes
     */
    private Long likes;

    /**
     * Number of downvotes
     */
    private Long dislikes;

    /**
     * Number of comments
     */
    private Long comments;

    /**
     * Computed score (likes - dislikes)
     */
    private Long score;

    /**
     * Current user's vote: 1 = upvote, -1 = downvote, 0 = no vote
     */
    private Integer userVote;

    /**
     * Topic name derived from problem tags (e.g., "Array", "Hash Table")
     */
    private String topicName;

    /**
     * Whether the solution is pinned to the top
     */
    private Boolean isPinned;

    /**
     * Author badges/achievements
     */
    private List<String> badges;

    /**
     * Author flair (primary badge)
     */
    private String flair;

    /**
     * Whether the solution is published
     */
    private Boolean isPublished;

    /**
     * When the solution was published
     */
    private LocalDateTime publishedAt;

    /**
     * Whether the solution is flagged
     */
    private Boolean isFlagged;

    /**
     * Record creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp
     */
    private LocalDateTime updatedAt;
}
