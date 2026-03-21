package com.ulticode.modules.forum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * DTO for updating an existing forum post.
 */
@Data
@Schema(description = "Update forum post request")
public class UpdatePostDTO {

    @Size(max = 500, message = "Title must not exceed 500 characters")
    @Schema(description = "Post title", example = "How to solve dynamic programming problems?")
    private String title;

    @Size(max = 2000, message = "Excerpt must not exceed 2000 characters")
    @Schema(description = "Post excerpt/summary", example = "I'm struggling with DP problems...")
    private String excerpt;

    @Size(max = 50000, message = "Body must not exceed 50000 characters")
    @Schema(description = "Post body content (markdown)", example = "## Problem\nI need help with...")
    private String body;

    @Schema(description = "Tags for the post", example = "[\"dp\", \"algorithm\", \"help\"]")
    private List<String> tags;

    @Schema(description = "Flair type", example = "question", allowableValues = {"announcement", "discussion", "showcase", "question", "hiring"})
    private String flairType;

    @Size(max = 50, message = "Flair label must not exceed 50 characters")
    @Schema(description = "Flair label", example = "Help Needed")
    private String flairLabel;

    @Schema(description = "Media attachments")
    private List<Object> media;

    @Schema(description = "Whether the post is pinned", example = "false")
    private Boolean isPinned;

    @Schema(description = "Whether the post is locked", example = "false")
    private Boolean isLocked;
}
