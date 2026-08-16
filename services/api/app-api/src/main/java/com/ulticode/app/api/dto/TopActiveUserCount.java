package com.ulticode.app.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Top-submitter aggregation row: the submitter id, submission count, and
 * latest submission activity timestamp over the analysis window. Backs
 * {@code findTopActiveUsers}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopActiveUserCount implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private Long submissionCount;
    private LocalDateTime lastActive;

    /**
     * Backward-compatible constructor for callers that only provide the user id and count.
     */
    public TopActiveUserCount(String userId, Long submissionCount) {
        this(userId, submissionCount, null);
    }
}
