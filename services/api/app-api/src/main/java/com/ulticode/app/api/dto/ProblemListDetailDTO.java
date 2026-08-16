package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity-free problem-list detail consumed by the Admin BFF via
 * {@link com.ulticode.app.api.service.ProblemListChainReadPort}.
 *
 * <p>Field set mirrors the legacy admin detail VO so the admin HTTP
 * response shape is preserved. The provider fills the list-owned columns
 * and the ordered {@link ProblemInListDTO} chain; the Admin consumer
 * enriches the author fields, sets the admin-view constants
 * ({@code isOwner=false}, {@code isSaved=false}, {@code viewer=null},
 * {@code categories=[]}) and computes {@link ProblemListStatsDTO} from
 * the problem statuses.
 */
public class ProblemListDetailDTO implements Serializable {
    private static final long serialVersionUID = 1L;


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
    private List<ProblemInListDTO> problems;
    private ProblemListStatsDTO stats;
    private ViewerStateDTO viewer;
    private List<CategoryOptionDTO> categories;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorUsername() { return authorUsername; }
    public void setAuthorUsername(String authorUsername) { this.authorUsername = authorUsername; }
    public Boolean getIsPublic() { return isPublic; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    public Boolean getIsFeatured() { return isFeatured; }
    public void setIsFeatured(Boolean isFeatured) { this.isFeatured = isFeatured; }
    public String getBannerTag() { return bannerTag; }
    public void setBannerTag(String bannerTag) { this.bannerTag = bannerTag; }
    public String getBannerIcon() { return bannerIcon; }
    public void setBannerIcon(String bannerIcon) { this.bannerIcon = bannerIcon; }
    public String getBannerTheme() { return bannerTheme; }
    public void setBannerTheme(String bannerTheme) { this.bannerTheme = bannerTheme; }
    public Integer getBannerOrder() { return bannerOrder; }
    public void setBannerOrder(Integer bannerOrder) { this.bannerOrder = bannerOrder; }
    public Boolean getIsSaved() { return isSaved; }
    public void setIsSaved(Boolean isSaved) { this.isSaved = isSaved; }
    public Boolean getIsOwner() { return isOwner; }
    public void setIsOwner(Boolean isOwner) { this.isOwner = isOwner; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<ProblemInListDTO> getProblems() { return problems; }
    public void setProblems(List<ProblemInListDTO> problems) { this.problems = problems; }
    public ProblemListStatsDTO getStats() { return stats; }
    public void setStats(ProblemListStatsDTO stats) { this.stats = stats; }
    public ViewerStateDTO getViewer() { return viewer; }
    public void setViewer(ViewerStateDTO viewer) { this.viewer = viewer; }
    public List<CategoryOptionDTO> getCategories() { return categories; }
    public void setCategories(List<CategoryOptionDTO> categories) { this.categories = categories; }

    /**
     * A problem within a list: the ordered relation ({@code sortOrder},
     * {@code addedAt}) joined with the Problem-owned item columns and its
     * tags. Flat shape so the admin HTTP payload matches the legacy VO.
     */
    public record ProblemInListDTO(
            Long id,
            String slug,
            String title,
            String difficulty,
            String status,
            Integer sortOrder,
            LocalDateTime addedAt,
            BigDecimal acceptanceRate,
            Boolean isPremium,
            Boolean hasSolution,
            List<TagDTO> tags) implements Serializable {
        private static final long serialVersionUID = 1L;


        public ProblemInListDTO {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    /** Lightweight tag projection attached to a list item. */
    public record TagDTO(String id, String label) implements Serializable {
        private static final long serialVersionUID = 1L;
}

    /** Solved/attempted/todo statistics computed from the item statuses. */
    public static class ProblemListStatsDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String listId;
        private Integer totalCount;
        private Integer solvedCount;
        private Integer attemptedCount;
        private Integer todoCount;
        private Double progress;

        public String getListId() { return listId; }
        public void setListId(String listId) { this.listId = listId; }
        public Integer getTotalCount() { return totalCount; }
        public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
        public Integer getSolvedCount() { return solvedCount; }
        public void setSolvedCount(Integer solvedCount) { this.solvedCount = solvedCount; }
        public Integer getAttemptedCount() { return attemptedCount; }
        public void setAttemptedCount(Integer attemptedCount) { this.attemptedCount = attemptedCount; }
        public Integer getTodoCount() { return todoCount; }
        public void setTodoCount(Integer todoCount) { this.todoCount = todoCount; }
        public Double getProgress() { return progress; }
        public void setProgress(Double progress) { this.progress = progress; }
    }

    /** Viewer-specific state; admin view keeps it null. */
    public static class ViewerStateDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Boolean isSaved;
        private String categoryId;

        public Boolean getIsSaved() { return isSaved; }
        public void setIsSaved(Boolean isSaved) { this.isSaved = isSaved; }
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    }

    /** Category option for dropdowns; admin view keeps it empty. */
    public static class CategoryOptionDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String name;
        private Integer sortOrder;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }
}
