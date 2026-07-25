package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One row per (contest, problem) — records the user who first solved the problem and
 * the time-from-start (seconds) it took.
 *
 * <p>The DB unique key {@code first_solve_records_contest_id_problem_id_key} is what
 * makes "is this the first solve?" answerable atomically. INSERT and check affected
 * rows.
 */
@Data
@TableName("first_solve_records")
public class FirstSolveRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String contestId;

    private Long problemId;

    private String userId;

    private LocalDateTime solvedAt;

    private Integer timeSpent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
