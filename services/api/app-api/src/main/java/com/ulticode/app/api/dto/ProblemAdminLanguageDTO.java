package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Entity-free problem-language projection (starter-code rows).
 *
 * <p>Mirrors the {@code problem_languages} columns consumed by the Admin
 * code-data tab.
 */
public record ProblemAdminLanguageDTO(
        String id,
        String label,
        String value,
        String style,
        String starterCode) implements Serializable {
}
