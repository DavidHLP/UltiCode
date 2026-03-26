package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin Contest View Object for admin panel API responses.
 * Contains all contest fields needed for admin management.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminContestVO {

    /**
     * Contest unique identifier
     */
    private String id;

    /**
     * URL-friendly identifier for the contest
     */
    private String slug;

    /**
     * Contest title
     */
    private String title;

    /**
     * Contest description
     */
    private String description;

    /**
     * Contest type: PUBLIC, PRIVATE, VIRTUAL
     */
    private String contestType;

    /**
     * Contest status: UPCOMING, RUNNING, FINISHED
     */
    private String status;

    /**
     * Contest start time
     */
    private LocalDateTime startTime;

    /**
     * Contest end time
     */
    private LocalDateTime endTime;

    /**
     * Contest duration in minutes
     */
    private Integer durationMinutes;

    /**
     * Whether the contest is visible
     */
    private Boolean isVisible;

    /**
     * Number of participants
     */
    private Integer participantCount;

    /**
     * Number of problems
     */
    private Integer problemCount;

    /**
     * When the contest was created
     */
    private LocalDateTime createdAt;

    /**
     * When the contest was last updated
     */
    private LocalDateTime updatedAt;
}
