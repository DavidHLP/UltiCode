package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Entity-free contest-problem view used by the Admin BFF after an owner write.
 */
public record ContestProblemAdminDTO(
        String id,
        String contestId,
        Long problemId,
        String problemIndex,
        Integer score,
        Integer penaltyPerWrong,
        String title,
        String slug,
        String difficulty,
        Integer solvedCount,
        Integer submissionCount,
        BigDecimal acceptanceRate) implements Serializable {
    private static final long serialVersionUID = 1L;

}
