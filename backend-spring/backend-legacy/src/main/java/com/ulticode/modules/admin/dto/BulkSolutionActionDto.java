package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for bulk operations on solutions (publish / unpublish / delete / unflag).
 *
 * <p>Flagging is intentionally excluded from bulk — it requires a per-solution reason.
 * Admins should use {@code POST /admin/solutions/{id}/flag} individually.
 */
@Data
public class BulkSolutionActionDto {

    /** Maximum number of solutions that can be processed in a single bulk call. */
    public static final int MAX_BULK_SIZE = 100;

    /** Allowed bulk actions. Referenced by {@code @Pattern} below and by the controller
     *  Swagger description to keep the two in sync. */
    public static final String ACTION_PATTERN = "publish|unpublish|delete|unflag";

    @NotEmpty(message = "Solution IDs must not be empty")
    @Size(max = MAX_BULK_SIZE, message = "Cannot process more than " + MAX_BULK_SIZE + " solutions at once")
    private List<@NotNull String> ids;

    @NotBlank(message = "Action must not be blank")
    @Pattern(
            regexp = ACTION_PATTERN,
            message = "Action must be one of: publish, unpublish, delete, unflag. "
                    + "To flag a solution, use POST /admin/solutions/{id}/flag individually with a reason.")
    private String action;
}
