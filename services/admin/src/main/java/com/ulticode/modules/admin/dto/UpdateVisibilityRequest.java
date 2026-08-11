package com.ulticode.modules.admin.dto;

import lombok.Data;

/**
 * Admin request DTO for updating a problem list's visibility
 * (public / featured flags).
 */
@Data
public class UpdateVisibilityRequest {

    private Boolean isPublic;
    private Boolean isFeatured;
}
