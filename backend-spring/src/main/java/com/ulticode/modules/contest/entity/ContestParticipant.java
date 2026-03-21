package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Contest participant entity
 */
@Data
@TableName("contest_participants")
public class ContestParticipant {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String contestId;

    private String userId;

    /**
     * Participant status: REGISTERED, PARTICIPATING, COMPLETED, DISQUALIFIED
     */
    private String status;

    private LocalDateTime registeredAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime checkedInAt;

    private Boolean isVirtual;

    private Integer finalRank;

    private Integer totalPenalty;

    private Integer totalScore;

    private Integer totalTime;

    private Integer totalAttempts;

    private Integer attemptCount;

    private Integer lastSolveTime;

    private String virtualSessionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
