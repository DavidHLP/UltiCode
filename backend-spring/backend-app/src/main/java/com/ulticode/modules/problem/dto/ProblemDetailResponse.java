package com.ulticode.modules.problem.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Problem detail response DTO for public API.
 * Contains full problem data including description, examples, and language options.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailResponse {

    private Long id;

    private String slug;

    private String title;

    @JsonProperty("difficulty")
    private String difficulty;

    @JsonProperty("acceptance_rate")
    private BigDecimal acceptanceRate;

    private String status;

    @JsonProperty("is_premium")
    private Boolean isPremium;

    @JsonProperty("has_solution")
    private Boolean hasSolution;

    @JsonProperty("completed_time")
    private LocalDateTime completedTime;

    @JsonProperty("is_published")
    private Boolean isPublished;

    @JsonProperty("published_at")
    private LocalDateTime publishedAt;

    @JsonProperty("published_by")
    private String publishedBy;

    @JsonProperty("is_deleted")
    private Boolean isDeleted;

    @JsonProperty("deleted_at")
    private LocalDateTime deletedAt;

    @JsonProperty("is_flagged")
    private Boolean isFlagged;

    @JsonProperty("flag_reason")
    private String flagReason;

    @JsonProperty("flag_reported_by")
    private String flagReportedBy;

    @JsonProperty("flag_reported_at")
    private LocalDateTime flagReportedAt;

    @JsonProperty("flag_status")
    private String flagStatus;

    @JsonProperty("flag_reviewed_by")
    private String flagReviewedBy;

    @JsonProperty("flag_reviewed_at")
    private LocalDateTime flagReviewedAt;

    @JsonProperty("flag_notes")
    private String flagNotes;

    @JsonProperty("submission_count")
    private Long submissionCount;

    @JsonProperty("solution_count")
    private Long solutionCount;

    private List<ProblemTagVO> tags;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    /**
     * Nested detail object containing description content
     */
    private DetailData detail;

    /**
     * Interaction counts
     */
    private InteractionData interactions;

    /**
     * Problem examples
     */
    private List<ExampleData> examples;

    /**
     * Language options with starter code
     */
    private List<LanguageData> languages;

    /**
     * Inner class for problem detail info
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DetailData {
        private String summary;

        private String content;

        @JsonProperty("constraints_json")
        private List<String> constraintsJson;

        private List<String> hints;

        @JsonProperty("follow_up")
        private String followUp;

        private List<CompanyInfo> companies;
    }

    /**
     * Inner class for company info
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompanyInfo {
        private String id;
        private String name;
        private String logo;
    }

    /**
     * Inner class for example data
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExampleData {
        private String id;

        @JsonAlias({"inputText", "input_text"})
        private String input;

        @JsonAlias({"outputText", "output_text"})
        private String output;

        private String explanation;

        private List<InputData> inputs;

        @JsonIgnore
        public String getInputText() {
            return input;
        }

        @JsonIgnore
        public void setInputText(String inputText) {
            this.input = inputText;
        }

        @JsonIgnore
        public String getOutputText() {
            return output;
        }

        @JsonIgnore
        public void setOutputText(String outputText) {
            this.output = outputText;
        }
    }

    /**
     * Inner class for structured input
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputData {
        private String name;
        private Object value;
    }

    /**
     * Inner class for language data
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LanguageData {
        private String id;
        private String label;
        private String value;
        private String style;

        @JsonProperty("starter_code")
        private String starterCode;
    }

    /**
     * Interaction counts
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InteractionData {
        private Integer likes;
        private Integer dislikes;
        private Integer favorites;
        @JsonProperty("viewer_reaction")
        private String viewerReaction;
    }

    /**
     * Tag information
     */
    @Data
    public static class ProblemTagVO {
        private String id;
        private String label;
    }
}
