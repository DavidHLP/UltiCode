package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Typed response for {@code POST /admin/settings/cache/clear}. The endpoint
 * is currently a no-op reserved for future invalidation hooks; the typed
 * shape pins the frontend contract instead of leaking {@code Map<String,Object>}
 * across the wire.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of clearing the settings cache")
public class ClearCacheResponseVO {

    @Schema(description = "List of cache scopes that were invalidated")
    private List<String> clearedScopes;

    @Schema(description = "Server-side timestamp of the clear operation")
    private String timestamp;
}
