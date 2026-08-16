package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * Entity-free problem-tag projection for administrative consumers.
 *
 * <p>Carries the full {@code problem_tags} row shape the Admin tag
 * management surface renders; {@code label} maps to the Admin VO's
 * {@code name} field.
 */
public record ProblemAdminTagDTO(
        String id,
        String label,
        String slug,
        String description,
        String color,
        Integer usageCount,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt) implements Serializable {
    private static final long serialVersionUID = 1L;

}
