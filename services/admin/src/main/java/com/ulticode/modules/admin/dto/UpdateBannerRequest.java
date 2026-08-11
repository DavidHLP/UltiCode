package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Admin request DTO for updating a problem list's banner settings.
 */
@Data
public class UpdateBannerRequest {

    @Size(max = 50, message = "Banner tag must not exceed 50 characters")
    private String bannerTag;

    private String bannerIcon;

    @Size(max = 20, message = "Banner theme must not exceed 20 characters")
    private String bannerTheme;

    private Integer bannerOrder;
}
