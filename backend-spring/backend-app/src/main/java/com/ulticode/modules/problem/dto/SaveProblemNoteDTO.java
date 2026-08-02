package com.ulticode.modules.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for saving (upserting) a user's problem note.
 * Used by POST /problems/{problemId}/note.
 *
 * @author Claude
 * @since 2026-06-11
 */
@Data
public class SaveProblemNoteDTO {

    /**
     * Note content. Must not be blank and must be at most 65535 characters.
     */
    @NotBlank(message = "Content is required")
    @Size(max = 65535, message = "Content must be at most 65535 characters")
    private String content;
}
