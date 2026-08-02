package com.ulticode.app.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Top-submitter aggregation row: the submitter id and their submission count
 * over the analysis window. Backs {@code findTopActiveUsers}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopActiveUserCount {
    private String userId;
    private Long submissionCount;
}
