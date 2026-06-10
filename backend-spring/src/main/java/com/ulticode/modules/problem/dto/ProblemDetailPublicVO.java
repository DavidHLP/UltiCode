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
 * Public-safe problem detail response DTO.
 * Excludes moderation and management fields.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetailPublicVO {

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

    @JsonProperty("submission_count")
    private Long submissionCount;

    @JsonProperty("solution_count")
    private Long solutionCount;

    private List<ProblemTagVO> tags;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    private DetailData detail;
    private InteractionData interactions;
    private List<ExampleData> examples;
    private List<LanguageData> languages;

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

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CompanyInfo {
        private String id;
        private String name;
        private String logo;
    }

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

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InputData {
        private String name;
        private Object value;
    }

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

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InteractionData {
        private Integer likes;
        private Integer dislikes;
        private Integer favorites;

        /**
         * D-10: this viewer's own reaction. Null when anonymous or no reaction.
         * Frontend mapper (console/src/api/problem-detail.ts) reads
         * {@code response.interactions.viewer.reaction}.
         */
        private ViewerData viewer;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ViewerData {
        /** "like" | "dislike" | "favorite" | null */
        private String reaction;
    }

    @Data
    public static class ProblemTagVO {
        private String id;
        private String label;
    }
}
