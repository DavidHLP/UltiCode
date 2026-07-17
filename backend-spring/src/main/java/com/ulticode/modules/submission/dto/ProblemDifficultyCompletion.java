package com.ulticode.modules.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-difficulty problem completion row: total problems and how many have at
 * least one accepted submission. Backs {@code countProblemCompletionByDifficulty}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemDifficultyCompletion {
    private String difficulty;
    private Long totalProblems;
    private Long solvedProblems;
}
