package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Entity-free Admin problem query filters.
 *
 * <p>Plain POJO (not a record) so Spring MVC can bind it directly as a
 * {@code @ModelAttribute} from query parameters, exactly like the legacy
 * module {@code ProblemQueryDTO} it replaces. Field set, defaults and the
 * {@code limit → pageSize} normalisation mirror that DTO so the Admin HTTP
 * surface is unchanged.
 */
public class ProblemAdminQueryDTO implements Serializable {

    private Integer page;
    private Integer pageSize;
    private Integer limit;
    private String difficulty;
    private String status;
    private String search;
    private String sortBy;
    private String sortOrder;
    private Boolean isPublished;
    private Boolean isDeleted;
    private String tag;
    private String publishStatus;
    private String category;
    private Boolean isPremium;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    /**
     * Normalize limit to pageSize for backward compatibility.
     */
    public Integer getPageSize() {
        if (pageSize != null) {
            return pageSize;
        }
        return limit;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Boolean getIsPublished() {
        return isPublished;
    }

    public void setIsPublished(Boolean isPublished) {
        this.isPublished = isPublished;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getPublishStatus() {
        return publishStatus;
    }

    public void setPublishStatus(String publishStatus) {
        this.publishStatus = publishStatus;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getIsPremium() {
        return isPremium;
    }

    public void setIsPremium(Boolean isPremium) {
        this.isPremium = isPremium;
    }
}
