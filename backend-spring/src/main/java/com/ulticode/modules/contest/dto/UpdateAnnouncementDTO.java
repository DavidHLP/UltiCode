package com.ulticode.modules.contest.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for updating a contest announcement.
 * All fields are optional for PATCH semantics.
 */
@Data
public class UpdateAnnouncementDTO {
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String content;

    private Boolean isPinned;
}
