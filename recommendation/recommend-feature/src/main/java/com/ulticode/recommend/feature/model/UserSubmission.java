package com.ulticode.recommend.feature.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a user's submission for feature extraction purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSubmission {

    /**
     * The ID of the problem submitted.
     */
    private Long problemId;

    /**
     * Whether the submission was accepted (passed all test cases).
     */
    private boolean accepted;

    /**
     * The timestamp when the submission was made.
     */
    private LocalDateTime timestamp;

    /**
     * The programming language used (optional).
     */
    private String language;

    /**
     * Runtime in milliseconds (optional).
     */
    private Integer runtime;

    /**
     * Memory usage in KB (optional).
     */
    private Integer memory;
}
