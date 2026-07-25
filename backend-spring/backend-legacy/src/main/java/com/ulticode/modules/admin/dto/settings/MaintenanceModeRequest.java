package com.ulticode.modules.admin.dto.settings;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for {@code POST /admin/settings/maintenance}.
 *
 * <p>{@code enabled} is a boxed {@link Boolean} (not a primitive) so that
 * {@code @NotNull} can distinguish a missing field ({@code null}) from an
 * explicit {@code false}.
 */
@Data
public class MaintenanceModeRequest {

    @NotNull(message = "enabled is required")
    private Boolean enabled;

    /** Optional message shown to users during maintenance. */
    private String message;
}
