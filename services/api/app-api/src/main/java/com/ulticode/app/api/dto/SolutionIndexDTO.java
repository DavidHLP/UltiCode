package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Minimal projection of a solution row for search indexing. Carries only
 * the fields external consumers (search, problem) need — never the full
 * entity. The full {@code Solution} entity remains internal to the
 * solution module.
 *
 * @param id solution ID
 * @param title solution title for search display
 * @param summary solution summary for search snippet
 * @param problemId parent problem ID for URL construction
 */
public record SolutionIndexDTO(
        String id,
        String title,
        String summary,
        Long problemId) implements Serializable {
    private static final long serialVersionUID = 1L;

}
