package com.ulticode.app.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Difficulty-count pair used by SubmissionUserStatsPort.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DifficultyCountDTO {
    private String difficulty;
    private Long count;
}
