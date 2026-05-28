package com.ulticode.modules.contest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for contest statistics.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContestStatsVO {

    /**
     * Contest unique identifier
     */
    private String contestId;

    /**
     * Contest title
     */
    private String title;

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
     * Number of registered participants
     */
    private Integer registeredParticipants;

    /**
     * Number of active participants
     */
    private Integer activeParticipants;

    /**
     * Number of completed participants
     */
    private Integer completedParticipants;

    /**
     * Total submissions in the contest
     */
    private Long totalSubmissions;

    /**
     * Total accepted submissions
     */
    private Long acceptedSubmissions;

    /**
     * Acceptance rate
     */
    private BigDecimal acceptanceRate;

    /**
     * Average score of all participants
     */
    private Long averageScore;

    /**
     * Highest score in the contest
     */
    private Long highestScore;

    /**
     * Lowest score among participants
     */
    private Long lowestScore;

    /**
     * Median score of all participants
     */
    private Long medianScore;

    /**
     * Average problems solved per participant
     */
    private BigDecimal averageProblemsSolved;

    /**
     * Total problems in the contest
     */
    private Integer totalProblems;

    /**
     * Easiest problem (highest acceptance rate)
     */
    private Long easiestProblemId;

    /**
     * Hardest problem (lowest acceptance rate)
     */
    private Long hardestProblemId;

    /**
     * Most attempted problem ID
     */
    private Long mostAttemptedProblemId;

    /**
     * Time remaining until contest starts (for upcoming contests)
     */
    private Long timeRemaining;

    /**
     * Time until contest ends (for active contests)
     */
    private Long timeUntilEnd;

    /**
     * Whether this is a premium contest
     */
    private Boolean isPremium;

    /**
     * Whether the contest is published
     */
    private Boolean isPublished;

    /**
     * Number of participants who finished
     */
    private Integer finishedParticipants;

    /**
     * Finish rate (percentage of participants who finished)
     */
    private BigDecimal finishRate;
}