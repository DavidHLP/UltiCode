package com.ulticode.app.api.dto;

import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * Entity-free contest snapshot consumed by backend-admin via
 * {@link com.ulticode.app.api.service.ContestAdminReadPort}.
 *
 * <p>Field set is the exact minimum derived from verified admin consumers:
 * DefaultAdminContestProjection (list/detail VO), AdminContestMutationServiceImpl
 * (audit before-state), DefaultAdminAnalyticsPortAdapter (analytics), and
 * AdminContestServiceImpl (detail).
 *
 * @author ulticode
 */
public class ContestAdminDTO implements Serializable {
    private static final long serialVersionUID = 1L;


    private String id;
    private String slug;
    private String title;
    private String description;
    private String contestType;
    private String scoringMode;
    private String status;
    private String creatorUserId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer durationMinutes;
    private Integer maxParticipants;
    private Integer registeredCount;
    private Boolean isPublished;
    private Boolean isPremium;
    private Boolean isVisible;
    private Boolean isDeleted;
    private Boolean isRated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getContestType() { return contestType; }
    public void setContestType(String contestType) { this.contestType = contestType; }
    public String getScoringMode() { return scoringMode; }
    public void setScoringMode(String scoringMode) { this.scoringMode = scoringMode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(String creatorUserId) { this.creatorUserId = creatorUserId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public LocalDateTime getActualStartTime() { return actualStartTime; }
    public void setActualStartTime(LocalDateTime actualStartTime) { this.actualStartTime = actualStartTime; }
    public LocalDateTime getActualEndTime() { return actualEndTime; }
    public void setActualEndTime(LocalDateTime actualEndTime) { this.actualEndTime = actualEndTime; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public Integer getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(Integer maxParticipants) { this.maxParticipants = maxParticipants; }
    public Integer getRegisteredCount() { return registeredCount; }
    public void setRegisteredCount(Integer registeredCount) { this.registeredCount = registeredCount; }
    public Boolean getIsPublished() { return isPublished; }
    public void setIsPublished(Boolean isPublished) { this.isPublished = isPublished; }
    public Boolean getIsPremium() { return isPremium; }
    public void setIsPremium(Boolean isPremium) { this.isPremium = isPremium; }
    public Boolean getIsVisible() { return isVisible; }
    public void setIsVisible(Boolean isVisible) { this.isVisible = isVisible; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }
    public Boolean getIsRated() { return isRated; }
    public void setIsRated(Boolean isRated) { this.isRated = isRated; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
