package com.ulticode.modules.forum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for forum quick filter options.
 * Represents a filter that users can apply to post listings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Quick filter option for forum posts")
public class QuickFilterDTO {

    @Schema(description = "Display label for the filter (will be translated on frontend)")
    private String label;

    @Schema(description = "Filter value identifier (e.g., 'hot', 'new', 'top')")
    private String value;
}
