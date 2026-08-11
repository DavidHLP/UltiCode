package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Entity-free problem-list summary consumed by the Admin BFF via
 * {@link com.ulticode.app.api.service.ProblemListSearchReadPort} and
 * {@link com.ulticode.app.api.service.ProblemListChainReadPort}.
 *
 * <p>Field set mirrors the legacy admin summary VO exactly so the admin
 * HTTP response shape is preserved. The provider fills the list-owned
 * columns plus {@code problemCount}; the Admin consumer enriches
 * {@code authorName} / {@code authorUsername} from its identity view and
 * may set {@code isSaved}.
 */
public class ProblemListSummaryDTO implements Serializable {

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
    private Integer problemCount;
    private Boolean isSaved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
    public Integer getProblemCount() { return problemCount; }
    public void setProblemCount(Integer problemCount) { this.problemCount = problemCount; }
    public Boolean getIsSaved() { return isSaved; }
    public void setIsSaved(Boolean isSaved) { this.isSaved = isSaved; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
