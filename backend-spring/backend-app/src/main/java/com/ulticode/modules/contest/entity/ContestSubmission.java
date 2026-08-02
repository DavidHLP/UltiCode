package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Contest submission entity - tracks submissions made during a contest.
 * Note: No updatedAt field (V3 table has no updated_at column).
 */
@Data
@TableName("contest_submissions")
public class ContestSubmission {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String submissionId;

    private String contestId;

    private String contestProblemId;

    private String participantId;

    private String virtualSessionId;

    private LocalDateTime submittedAt;

    private Integer timeFromStart;

    private Boolean isAccepted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
