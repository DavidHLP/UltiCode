package com.ulticode.modules.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Typed projection of {@code submissions} aggregated by status.
 * Replaces the previous {@code Map<String, Object>} leakage at the
 * submission persistence seam. Both admin and submission modules use
 * this DTO so a typo in the map key can never be a runtime bug.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusCountDTO {
    /** Submission status string (e.g. "Accepted", "WrongAnswer"). */
    private String status;
    /** Number of submissions in this status. */
    private Long count;
}
