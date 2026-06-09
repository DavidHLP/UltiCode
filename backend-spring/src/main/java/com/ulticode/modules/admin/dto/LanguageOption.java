package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Language option for filtering submissions.
 *
 * <p>Mirrors the shape of {@link StatusOption} so the admin UI can render
 * both filter dropdowns the same way. Field {@link #key} is the raw
 * language code stored in the database (e.g. {@code "cpp"}); field
 * {@link #label} is a humanised display name (e.g. {@code "C++"}).</p>
 */
@Data
@Schema(description = "Submission language option")
public class LanguageOption {

    /** DB-stored language code, e.g. {@code "cpp"}, {@code "javascript"}. */
    @Schema(description = "DB-stored language code used for filtering", example = "cpp")
    private String key;

    /** Human-readable display label, e.g. {@code "C++"}, {@code "JavaScript"}. */
    @Schema(description = "Display label for the language", example = "C++")
    private String label;
}
