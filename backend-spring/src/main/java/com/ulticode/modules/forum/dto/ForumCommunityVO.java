package com.ulticode.modules.forum.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Forum Community View Object for API responses.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForumCommunityVO {

    /**
     * Community unique identifier
     */
    private String id;

    /**
     * Community display name
     */
    private String name;

    /**
     * URL-friendly slug (unique)
     */
    private String slug;

    /**
     * Community description
     */
    private String description;

    /**
     * Number of members
     */
    private Integer members;

    /**
     * Number of online members
     */
    private Integer online;

    /**
     * Icon URL or identifier
     */
    private String icon;

    /**
     * Theme color (hex code)
     */
    private String color;

    /**
     * Banner image URL
     */
    private String banner;

    /**
     * Total number of posts
     */
    private Integer postsCount;

    /**
     * Number of posts today
     */
    private Integer postsToday;

    /**
     * Number of posts this week
     */
    private Integer postsWeek;

    /**
     * Whether this is an official community
     */
    private Boolean isOfficial;

    /**
     * Whether this community is featured
     */
    private Boolean isFeatured;

    /**
     * Sort order for display
     */
    private Integer sortOrder;

    /**
     * Community visibility (PUBLIC, PRIVATE)
     */
    private String visibility;

    /**
     * Whether current user is a member
     */
    private Boolean isMember;

    /**
     * Current user's role in community (if member)
     */
    private String userRole;

    /**
     * Record creation timestamp
     */
    private LocalDateTime createdAt;
}
