package com.ulticode.modules.contest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Response DTO for contest rankings.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContestRankingVO {

    /**
     * Contest unique identifier
     */
    private String contestId;

    /**
     * Contest title
     */
    private String title;

    /**
     * Contest slug
     */
    private String slug;

    /**
     * User rank in the contest
     */
    private Integer rank;

    /**
     * User ID
     */
    private String userId;

    /**
     * Username
     */
    private String username;

    /**
     * User display name
     */
    private String name;

    /**
     * User avatar URL
     */
    private String avatar;

    /**
     * User score in the contest
     */
    private Long score;

    /**
     * Time bonus (if any)
     */
    private Long timeBonus;

    /**
     * Penalty time
     */
    private Long penalty;

    /**
     * Number of problems solved
     */
    private Integer problemsSolved;

    /**
     * Contest start time
     */
    private LocalDateTime startTime;

    /**
     * User's finish time
     */
    private LocalDateTime finishTime;

    /**
     * Total participants in the contest
     */
    private Integer totalParticipants;

    /**
     * Whether this is the user's own ranking
     */
    private Boolean isCurrentUser;

    /**
     * Progress percentage for the user
     */
    private BigDecimal progress;

    /**
     * Whether the user is in the top percentage
     */
    private Boolean isInTopPercentile;

    /**
     * User's percentile ranking
     */
    private BigDecimal percentile;

    /**
     * Whether the user is participating
     */
    private Boolean isParticipating;

    /**
     * Whether the contest is active
     */
    private Boolean isActive;

    /**
     * User's country
     */
    private String country;

    /**
     * Maximum rating achieved
     */
    private Integer maxRating;

    /**
     * Current rating title
     */
    private String ratingTitle;

    /**
     * Maximum rating title achieved
     */
    private String maxRatingTitle;

    /**
     * Number of contests attended
     */
    private Integer contestsAttended;

    /**
     * User's badge
     */
    private String badge;
}