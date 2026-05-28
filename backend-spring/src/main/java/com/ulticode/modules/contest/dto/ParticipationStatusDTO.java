package com.ulticode.modules.contest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for user participation status in contests.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParticipationStatusDTO {

    /**
     * Contest unique identifier
     */
    private String contestId;

    /**
     * Contest title
     */
    private String title;

    /**
     * User participation status: registered, participating, completed, not_participated
     */
    private String status;

    /**
     * When the user registered for the contest
     */
    private LocalDateTime registeredAt;

    /**
     * When the user started the contest
     */
    private LocalDateTime startedAt;

    /**
     * When the user completed the contest
     */
    private LocalDateTime completedAt;

    /**
     * Contest start time
     */
    private LocalDateTime startTime;

    /**
     * Contest end time
     */
    private LocalDateTime endTime;

    /**
     * User's ranking in the contest
     */
    private Integer ranking;

    /**
     * User's score in the contest
     */
    private Long score;

    /**
     * Number of problems solved by user in the contest
     */
    private Integer problemsSolved;

    /**
     * Total number of problems in the contest
     */
    private Integer totalProblems;

    /**
     * Whether the user has started the contest
     */
    private Boolean hasStarted;

    /**
     * Whether the contest is currently active for the user
     */
    private Boolean isActive;

    /**
     * Whether the contest is finished for the user
     */
    private Boolean isCompleted;

    /**
     * Whether the user can still participate
     */
    private Boolean canParticipate;
}