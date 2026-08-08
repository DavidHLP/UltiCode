package com.ulticode.modules.contest.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Scoring-rule configuration row — admin-side metadata only.
 *
 * <p>The runtime scoring path (judge-result application, ranking sort,
 * wrong-submission penalty) does <strong>not</strong> consult this
 * entity. Instead it delegates to the
 * {@link com.ulticode.modules.contest.scoring.ScoringStrategy} keyed on
 * {@link com.ulticode.modules.contest.entity.enums.ContestScoringMode}.
 *
 * <p>Why two surfaces? {@code ScoringStrategy} is the behaviour that
 * actually drives the score / penalty / sort; this entity is the
 * admin-editable tuning sheet (per-problem weight templates) that
 * surfaces in the management UI. They are intentionally separate so
 * that changing the runtime contract (Card 3, 2026-07 architecture
 * review) does not require a migration or admin re-publish.
 */
@Data
@TableName("contest_scoring_rules")
public class ScoringRule {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;

    private String description;

    private Integer baseScorePerProblem;

    private Integer timeBonusPerMinute;

    private Integer wrongAnswerPenalty;

    private Integer timeLimitPenalty;

    private Integer firstSolveBonus;

    private Integer fullScoreBonus;

    private Boolean isDefault;

    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
