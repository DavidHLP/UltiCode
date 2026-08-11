package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Contest-problem attachment requested by an owner write command.
 * The DTO contains no contest implementation types.
 */
public record ContestProblemInputDTO(Long problemId, Integer score) implements Serializable {

    public ContestProblemInputDTO {
        if (problemId == null) {
            throw new IllegalArgumentException("problemId is required");
        }
        if (score != null && score <= 0) {
            throw new IllegalArgumentException("score must be positive");
        }
    }
}
