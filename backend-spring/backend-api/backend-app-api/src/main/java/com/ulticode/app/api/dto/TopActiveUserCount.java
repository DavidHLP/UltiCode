package com.ulticode.app.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Top-submitter aggregation row: the submitter id and their submission count
 * over the analysis window. Backs {@code findTopActiveUsers}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopActiveUserCount implements Serializable {
    private String userId;
    private Long submissionCount;
}
