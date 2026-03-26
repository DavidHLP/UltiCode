package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Status option for filtering submissions.
 */
@Data
@Schema(description = "Submission status option")
public class StatusOption {

    @Schema(description = "Status key/value")
    private String key;

    @Schema(description = "Display label")
    private String label;

    @Schema(description = "Status category (pending, accepted, error, etc.)")
    private String category;
}
