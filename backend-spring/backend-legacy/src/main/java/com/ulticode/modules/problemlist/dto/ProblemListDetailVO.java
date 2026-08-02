package com.ulticode.modules.problemlist.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * View object for detailed problem list response.
 */
@Data
public class ProblemListDetailVO {
    private String id;
    private String name;
    private String description;
    private String authorId;
    private String authorName;
    private String authorUsername;
    private Boolean isPublic;
    private Boolean isFeatured;
    private String bannerTag;
    private String bannerIcon;
    private String bannerTheme;
    private Integer bannerOrder;
    private Boolean isSaved;
    private Boolean isOwner;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProblemInListVO> problems;
    private ProblemListStatsVO stats;
    private ViewerStateVO viewer;
    private List<CategoryOptionVO> categories;

    /**
     * View object for problem list statistics.
     */
    @Data
    public static class ProblemListStatsVO {
        private String listId;
        private Integer totalCount;
        private Integer solvedCount;
        private Integer attemptedCount;
        private Integer todoCount;
        private Double progress;
    }

    /**
     * View object for viewer-specific state.
     */
    @Data
    public static class ViewerStateVO {
        private Boolean isSaved;
        private String categoryId;
    }

    /**
     * View object for category option (lightweight, for dropdowns).
     */
    @Data
    public static class CategoryOptionVO {
        private String id;
        private String name;
        private Integer sortOrder;
    }

    /**
     * View object for problem within a list.
     */
    @Data
    public static class ProblemInListVO {
        private Long id;
        private String slug;
        private String title;
        private String difficulty;
        private String status;
        private Integer sortOrder;
        private LocalDateTime addedAt;
        private java.math.BigDecimal acceptanceRate;
        private Boolean isPremium;
        private Boolean hasSolution;
        private java.util.List<ProblemTagVO> tags;

        /**
         * Lightweight tag projection for problem list items.
         */
        @Data
        public static class ProblemTagVO {
            private String id;
            private String label;
        }
    }
}
