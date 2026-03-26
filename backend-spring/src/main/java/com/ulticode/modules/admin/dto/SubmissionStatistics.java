package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Statistics about submissions for admin dashboard.
 */
@Data
@Schema(description = "Submission statistics")
public class SubmissionStatistics {

    @Schema(description = "Total number of submissions")
    private Long total;

    @Schema(description = "Submissions grouped by status")
    private List<StatusCount> byStatus;

    @Schema(description = "Submissions grouped by language")
    private List<LanguageCount> byLanguage;

    @Schema(description = "Number of submissions in the last 24 hours")
    private Long last24h;

    @Schema(description = "Number of pending submissions")
    private Long pending;

    /**
     * Status count record.
     */
    @Data
    @Schema(description = "Status count")
    public static class StatusCount {
        @Schema(description = "Status name")
        private String status;

        @Schema(description = "Number of submissions with this status")
        private Long count;
    }

    /**
     * Language count record.
     */
    @Data
    @Schema(description = "Language count")
    public static class LanguageCount {
        @Schema(description = "Programming language name")
        private String language;

        @Schema(description = "Number of submissions in this language")
        private Long count;
    }
}
