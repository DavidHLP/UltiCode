package com.ulticode.modules.forum.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Forum Tag View Object for API responses.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForumTagVO {

    /**
     * Tag unique identifier
     */
    private String id;

    /**
     * Tag display name
     */
    private String name;

    /**
     * URL-friendly slug (unique)
     */
    private String slug;

    /**
     * Tag description
     */
    private String description;

    /**
     * Tag color (hex code)
     */
    private String color;

    /**
     * Number of times this tag has been used
     */
    private Integer usageCount;

    /**
     * Record creation timestamp
     */
    private LocalDateTime createdAt;
}
