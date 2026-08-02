package com.ulticode.modules.submission.dto;

import lombok.Data;

/**
 * DTO for language-based submission statistics.
 */
@Data
public class LanguageStatsDTO {
    private String language;
    private Integer count;
}
