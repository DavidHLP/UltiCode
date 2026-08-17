package com.ulticode.submission.api.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * DTO for language-based submission statistics.
 */
@Data
public class LanguageStatsDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String language;
    private Integer count;
}
