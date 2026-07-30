package com.ulticode.modules.admin.dto.settings;

import lombok.Data;

/**
 * Rate limit settings expressed as requests-per-window strings.
 *
 * <p>Values are stored as strings (e.g. {@code "100"}) to match the existing
 * admin UI which renders free-form numeric input.
 */
@Data
public class RateLimitSettingsVO {

    private String rateLimitApi;
    private String rateLimitSubmission;
    private String rateLimitAuth;
    private String rateLimitUpload;
}
