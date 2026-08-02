package com.ulticode.app.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Typed projection of {@code submissions} aggregated by language.
 * Replaces the previous {@code Map<String, Object>} leakage at the
 * submission persistence seam.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LanguageCountDTO {
    /** Programming language (e.g. "java", "python"). */
    private String language;
    /** Number of submissions in this language. */
    private Long count;
}
