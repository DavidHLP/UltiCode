package com.ulticode.submission.api.dto;

import java.util.List;
import java.io.Serializable;

/**
 * Read-back DTO for batch rejudge operations.
 *
 * <p>Mirrors {@code BatchRejudgeResponse}: the total count, per-submission
 * results, and success/failure tallies. Each entry in {@code results}
 * corresponds to a single {@link RejudgeResultDTO}.
 *
 * @param total     total number of submissions in the batch
 * @param successful count of successfully initiated rejudges
 * @param failed     count of failed rejudges
 * @param results    per-submission rejudge results
 */
public record BatchRejudgeResultDTO(
        int total,
        int successful,
        int failed,
        List<RejudgeResultDTO> results) implements Serializable {
    private static final long serialVersionUID = 1L;

}
