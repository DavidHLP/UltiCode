package com.ulticode.modules.admin.dto.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Description data VO for problem description tab.
 * Contains problem details, examples, constraints, and tags.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DescriptionDataVO {

    private String id;

    private String title;

    private String slug;

    private String difficulty;

    private String status;

    private Boolean isPremium;

    private Boolean isPublished;

    /**
     * Nested detail object
     */
    private DetailInfo detail;

    /**
     * Problem tags
     */
    private List<ProblemTagVO> tags;

    /**
     * Problem examples
     */
    private List<ProblemExampleVO> examples;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime publishedAt;

    /**
     * Inner class for detail info
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DetailInfo {
        private String summary;
        private String content;
        private List<String> constraintsJson;
        private List<String> hints;
    }
}
