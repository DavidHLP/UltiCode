package com.ulticode.modules.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin Submission View Object for API responses.
 * Contains all submission fields needed for admin management including nested user and problem info.
 */
@Data
@Schema(description = "Admin submission view object")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminSubmissionVO {

    @Schema(description = "Submission unique identifier")
    private String id;

    @Schema(description = "Problem ID")
    private Long problemId;

    @Schema(description = "Problem title")
    private String problemTitle;

    @Schema(description = "Problem slug (URL-friendly identifier)")
    private String problemSlug;

    @Schema(description = "User ID who submitted")
    private String userId;

    @Schema(description = "Username of the submitter")
    private String username;

    @Schema(description = "Programming language used")
    private String language;

    @Schema(description = "Submission status (Pending, Accepted, Wrong Answer, etc.)")
    private String status;

    @Schema(description = "Runtime in milliseconds")
    private Integer runtime;

    @Schema(description = "Memory usage in megabytes")
    private Double memory;

    @Schema(description = "Submission creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Code length in characters")
    private Integer codeLength;

    // Detail fields (only included in detail view)

    @Schema(description = "Source code content")
    private String code;

    @Schema(description = "Additional notes")
    private String notes;

    @Schema(description = "Runtime percentile compared to other submissions")
    private Double runtimePercentile;

    @Schema(description = "Memory percentile compared to other submissions")
    private Double memoryPercentile;

    @Schema(description = "Test case execution details (JSON)")
    private Object testDetails;

    @Schema(description = "Memory distribution bins in MB (JSON)")
    private Object memoryDistBinsMb;

    @Schema(description = "Runtime distribution bins in ms (JSON)")
    private Object runtimeDistBinsMs;
}
