package com.ulticode.app.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Difficulty-count pair used by SubmissionUserStatsPort.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DifficultyCountDTO implements Serializable {
    private String difficulty;
    private Long count;
}
