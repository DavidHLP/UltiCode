package com.ulticode.app.api.dto;

import lombok.Data;

/**
 * DTO for language-based submission statistics.
 */
@Data
public class LanguageStatsDTO {
    private String language;
    private Integer count;
}
