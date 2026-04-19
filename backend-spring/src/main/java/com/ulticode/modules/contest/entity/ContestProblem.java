package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Contest problem entity - maps problems to contests.
 */
@Data
@TableName("contest_problems")
public class ContestProblem {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String contestId;

    /**
     * Problem ID (bigint in DB).
     */
    private Long problemId;

    private String problemIndex;

    private Integer score;

    private Integer penaltyPerWrong;

    private Integer solvedCount;

    private Integer submissionCount;

    private String label;

    private Integer baseScore;

    private Integer timeBonus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
