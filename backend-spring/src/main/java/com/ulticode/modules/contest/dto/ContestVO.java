package com.ulticode.modules.contest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contest View Object for API responses.
 * Contains all fields needed for the frontend.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContestVO {

    /**
     * Contest unique identifier (UUID string)
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
     * Contest status: upcoming, active, finished, cancelled
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
    private Integer duration;

    /**
     * Maximum number of participants
     */
    private Integer maxParticipants;

    /**
     * Current number of participants
     */
    private Integer currentParticipants;

    /**
     * Whether this is a premium contest
     */
    private Boolean isPremium;

    /**
     * Whether the contest is published
     */
    private Boolean isPublished;

    /**
     * When the contest was published
     */
    private LocalDateTime publishedAt;

    /**
     * Record creation timestamp
     */
    private LocalDateTime createdAt;

    /**
     * Record last update timestamp
     */
    private LocalDateTime updatedAt;

    /**
     * Contest creator ID
     */
    private Long createdById;

    /**
     * Contest creator username
     */
    private String createdByUsername;

    /**
     * List of problem IDs in the contest
     */
    private List<Long> problemIds;

    /**
     * List of tags associated with the contest
     */
    private List<String> tags;

    /**
     * Whether the user is participating in this contest
     */
    private Boolean isParticipating;

    /**
     * User's ranking in the contest (null if not participating)
     */
    private Integer userRanking;

    /**
     * User's score in the contest (null if not participating)
     */
    private Long userScore;

    /**
     * Contest type/format: ICPC, IOI, CUSTOM
     */
    private String contestType;

    /**
     * Whether the contest is visible to users
     */
    private Boolean isVisible;

    /**
     * Number of participants
     */
    private Integer participantCount;

    /**
     * Number of problems in the contest
     */
    private Integer problemCount;

    /**
     * Associated scoring rule ID
     */
    private String scoringRuleId;
}