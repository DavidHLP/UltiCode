package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Contest entity
 */
@Data
@TableName("contests")
public class Contest {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;

    private String slug;

    /**
     * Contest type: ICPC, IOI, CUSTOM
     */
    private String contestType;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime actualStartTime;

    private LocalDateTime actualEndTime;

    private Integer durationMinutes;

    private LocalDateTime registrationStart;

    private LocalDateTime registrationEnd;

    private LocalDateTime freezeTime;

    /**
     * Contest status: DRAFT, UPCOMING, RUNNING, FINISHED, CANCELLED
     */
    private String status;

    /**
     * Penalty seconds per wrong submission (default 300)
     */
    private Integer penaltyPerWrong;

    /**
     * Scoring mode: SCORE, ICPC, IOI
     */
    private String scoringMode;

    /**
     * Tie breaker: LAST_SOLVE_TIME, TOTAL_TIME, TOTAL_ATTEMPTS, NONE
     */
    private String tieBreaker;

    private Boolean isVirtual;

    private Integer maxParticipants;

    private Integer registeredCount;

    private Integer participantCount;

    private Integer submissionCount;

    private Boolean isRated;

    private String scoringRuleId;

    private String description;

    private String coverImage;

    private String createdBy;

    private Boolean isVisible;

    private String rules;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean isDeleted;

    private LocalDateTime deletedAt;

    private String deletedBy;
}
