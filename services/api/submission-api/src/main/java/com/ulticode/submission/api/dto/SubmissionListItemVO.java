package com.ulticode.submission.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import java.io.Serializable;

import java.time.LocalDateTime;

/**
 * Lightweight submission list item DTO.
 * Excludes code, tests, error details for efficient list queries.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubmissionListItemVO implements Serializable {
    private static final long serialVersionUID = 1L;


    private String id;
    private String status;
    private String language;
    private Integer runtime;
    private Double memory;
    private LocalDateTime createdAt;
    private String notes;

    /**
     * Simplified problem info
     */
    private ProblemSummary problem;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProblemSummary implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long id;
        private String title;
        private String slug;
    }
}
