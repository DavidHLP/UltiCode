package com.ulticode.app.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ADMIN-007: flat, entity-free projection of a {@code forum_tags} row
 * returned by {@link com.ulticode.app.api.service.ForumTagAdministrationService}
 * after a mutation.
 *
 * @param id          tag unique identifier
 * @param name        tag display name
 * @param slug        URL-friendly slug (unique)
 * @param description tag description
 * @param color       tag color (hex code)
 * @param usageCount  number of times the tag has been used
 * @param createdAt   record creation timestamp
 */
public record ForumTagDTO(
        String id,
        String name,
        String slug,
        String description,
        String color,
        Integer usageCount,
        LocalDateTime createdAt) implements Serializable {
}
