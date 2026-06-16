package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Per-(participant, problem) result row. Was a dead table prior to P0-1 fix; the contest
 * scoring service now writes one row per accepted verdict.
 *
 * <p>DB unique key on (participant_id, contest_problem_id) is what makes
 * INSERT-or-find idempotent for repeat-AC events.
 */
@Data
@TableName("contest_problem_results")
public class ContestProblemResult {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String contestId;

    private String contestProblemId;

    private String userId;

    private String participantId;

    private String rankingId;

    private Boolean isSolved;

    private Integer score;

    private Integer attempts;

    /** Time-from-start (seconds) of the first AC, or null if not yet solved. */
    private Integer firstSolveTime;

    /** Total penalty (ICPC: 20 min per wrong before AC) for this problem. */
    private Integer penaltyTime;

    private String bestSubmissionId;

    private Integer timeSpent;

    private Integer timeBonus;

    private Boolean isFirstSolve;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
