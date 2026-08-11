package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * ADMIN-007: flat, entity-free projection of a {@code forum_communities}
 * row for the Admin community filter-dropdown read.
 *
 * @param id          community unique identifier
 * @param name        community display name
 * @param slug        community slug (URL-safe identifier)
 * @param description community description
 * @param postCount   number of posts in the community (denormalized counter)
 * @param memberCount number of members in the community (denormalized counter)
 */
public record AdminForumCommunityDTO(
        String id,
        String name,
        String slug,
        String description,
        Integer postCount,
        Integer memberCount) implements Serializable {
}
