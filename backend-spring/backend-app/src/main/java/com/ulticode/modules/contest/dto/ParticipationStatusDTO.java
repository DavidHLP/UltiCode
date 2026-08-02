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
     * Virtual session ID (only for virtual contests; null for regular contests).
     * Frontend uses this to call virtual/finish.
     */
    private String id;

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
     * Contest end time.
     *
     * @deprecated Since 2026-06-11 the frontend's {@code VirtualContestSession} type
     *             uses {@link #endsAt} (ISO string). Retain {@code endTime} for
     *             MapStruct compatibility with {@code ContestVO.contest.endTime};
     *             remove in a future breaking-change release.
     */
    @Deprecated
    private LocalDateTime endTime;

    /**
     * When the virtual session ends (=endTime, exposed as ISO string for frontend).
     * Frontend's VirtualContestSession type uses this field name.
     */
    private LocalDateTime endsAt;

    /**
     * User's ranking in the contest
     */
    private Integer ranking;

    /**
     * User's score in the contest
     */
    private Long score;

    /**
     * User's penalty time in the contest (seconds), mirroring ContestParticipant.totalPenalty.
     */
    private Integer penalty;

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