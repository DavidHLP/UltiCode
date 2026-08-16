package com.ulticode.app.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.io.Serializable;

/**
 * Minimal Problem projection used by problem-list reads.
 *
 * <p>List relations own ordering and timestamps; this record contains only
 * the Problem-owned columns needed to render a list item. Missing tag rows
 * are represented by an empty list.
 */
public record ProblemListItemDTO(
        Long id,
        String slug,
        String title,
        String difficulty,
        String status,
        BigDecimal acceptanceRate,
        Boolean isPremium,
        Boolean hasSolution,
        List<Tag> tags) implements Serializable {
    private static final long serialVersionUID = 1L;


    public ProblemListItemDTO {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** Lightweight tag projection attached to a Problem list item. */
    public record Tag(String id, String label) implements Serializable {
        private static final long serialVersionUID = 1L;
}
}
