package com.ulticode.submission.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Typed projection of {@code submissions} aggregated by language.
 * Replaces the previous {@code Map<String, Object>} leakage at the
 * submission persistence seam.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LanguageCountDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Programming language (e.g. "java", "python"). */
    private String language;
    /** Number of submissions in this language. */
    private Long count;
}
