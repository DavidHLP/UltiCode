package com.ulticode.modules.admin.dto.settings;

import lombok.Data;

/**
 * Upload configuration.
 */
@Data
public class UploadSettingsVO {

    /** Maximum allowed file size, e.g. {@code "10MB"}. */
    private String uploadMaxSize;

    /** Comma-separated list of permitted extensions, e.g. {@code "jpg,png,zip"}. */
    private String uploadAllowedTypes;

    /** Maximum number of files per upload request. */
    private String uploadMaxFiles;
}
