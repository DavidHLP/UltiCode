package com.ulticode.submission.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Per-difficulty problem completion row: total problems and how many have at
 * least one accepted submission. Backs {@code countProblemCompletionByDifficulty}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemDifficultyCompletion implements Serializable {
    private static final long serialVersionUID = 1L;

    private String difficulty;
    private Long totalProblems;
    private Long solvedProblems;
}
