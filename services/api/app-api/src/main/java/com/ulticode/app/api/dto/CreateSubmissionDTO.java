package com.ulticode.app.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.io.Serializable;

/**
 * DTO for creating a new submission.
 */
@Data
public class CreateSubmissionDTO implements Serializable {

    @NotNull(message = "Problem ID is required")
    private Long problemId;

    @NotBlank(message = "Language is required")
    private String language;

    @NotBlank(message = "Code cannot be empty")
    private String code;

    /**
     * Explicit contest context. When absent, this is an ordinary submission
     * and it must not be inferred from currently running contests.
     */
    private String contestId;

    /**
     * Virtual session context, supplied by the contest admission seam for
     * virtual submissions. Real submissions leave this null.
     */
    private String virtualSessionId;
}
