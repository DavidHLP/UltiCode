package com.ulticode.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin Forum Community View Object for the admin panel community list
 * endpoint. Extracted from {@code AdminForumController} as a top-level DTO so
 * the {@link com.ulticode.modules.admin.projection.AdminForumProjection}
 * deep module can own the read shape without importing a controller inner
 * class (ADR-0011 Stage 2 hygiene).
 *
 * @author ulticode
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminForumCommunityVO {

    /**
     * Community unique identifier
     */
    private String id;

    /**
     * Community display name
     */
    private String name;

    /**
     * Community slug (URL-safe identifier)
     */
    private String slug;

    /**
     * Community description
     */
    private String description;

    /**
     * Number of posts in the community (denormalized counter)
     */
    private Integer postCount;

    /**
     * Number of members in the community (denormalized counter)
     */
    private Integer memberCount;
}
