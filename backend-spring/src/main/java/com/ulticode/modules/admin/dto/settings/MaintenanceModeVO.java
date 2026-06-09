package com.ulticode.modules.admin.dto.settings;

import lombok.Data;

/**
 * Response body for {@code POST /admin/settings/maintenance}.
 */
@Data
public class MaintenanceModeVO {

    private boolean maintenanceMode;
    private String message;
}
