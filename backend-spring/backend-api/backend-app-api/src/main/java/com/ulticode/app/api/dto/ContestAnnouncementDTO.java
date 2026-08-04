package com.ulticode.app.api.dto;

import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * Entity-free contest announcement snapshot consumed by backend-admin
 * via {@link com.ulticode.app.api.service.ContestAnnouncementReadPort}.
 *
 * <p>P7-RELOCATE-CONTEST-001 AC #7.
 *
 * @author ulticode
 */
public class ContestAnnouncementDTO implements Serializable {

    private String id;
    private String contestId;
    private String title;
    private String content;
    private Boolean isPinned;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContestId() { return contestId; }
    public void setContestId(String contestId) { this.contestId = contestId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getIsPinned() { return isPinned; }
    public void setIsPinned(Boolean isPinned) { this.isPinned = isPinned; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
