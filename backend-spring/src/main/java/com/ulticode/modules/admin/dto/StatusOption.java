package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Status option for filtering submissions.
 *
 * <p>Field {@link #key} carries the exact string stored in
 * {@code submissions.status} (e.g. {@code "Compile Error"} with a space),
 * so the admin UI can issue a filter that matches DB values directly.
 * Field {@link #code} carries the {@link com.ulticode.modules.submission.enums.SubmissionStatus}
 * enum name (e.g. {@code "COMPILE_ERROR"}) for stable programmatic
 * reference; i18n keys in the frontend map onto this code.</p>
 */
@Data
@Schema(description = "Submission status option")
public class StatusOption {

    /** DB-stored status string, e.g. {@code "Compile Error"}. */
    @Schema(description = "DB-stored status key used for filtering", example = "Compile Error")
    private String key;

    /** Human-readable label, typically same as {@link #key} in English. */
    @Schema(description = "Display label for the status")
    private String label;

    /** Coarse filter category: {@code pending}, {@code accepted}, {@code error}, {@code system}. */
    @Schema(description = "Status category for grouping (pending, accepted, error, system)")
    private String category;

    /** Stable enum-style code, e.g. {@code COMPILE_ERROR}. */
    @Schema(description = "Stable enum-style code for programmatic filtering", example = "COMPILE_ERROR")
    private String code;
}
