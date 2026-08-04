package com.ulticode.app.api.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * DTO for language-based submission statistics.
 */
@Data
public class LanguageStatsDTO implements Serializable {
    private String language;
    private Integer count;
}
