package com.ulticode.auth.api.dto;

import java.io.Serializable;

/**
 * Query filter parameters for Auth account administrative lists.
 *
 * <p>Supported filters:
 * <ul>
 *   <li>{@link #search()} &mdash; fuzzy substring match against {@code username} or {@code email};</li>
 *   <li>{@link #role()} &mdash; exact role filter (e.g. {@code "USER"}, {@code "ADMIN"});</li>
 *   <li>{@link #active()} &mdash; active lifecycle filter;</li>
 *   <li>{@link #banned()} &mdash; ban lifecycle filter;</li>
 *   <li>{@link #page()}, {@link #limit()} &mdash; 1-based pagination (limit capped at 100);</li>
 *   <li>{@link #sortBy()}, {@link #sortOrder()} &mdash; sort column and direction.</li>
 * </ul>
 */
public record AccountQueryDTO(
        String search,
        String role,
        Boolean active,
        Boolean banned,
        int page,
        int limit,
        String sortBy,
        String sortOrder,
        boolean usernameOnly) implements Serializable {
    private static final long serialVersionUID = 1L;

    public AccountQueryDTO(
            String search, String role, Boolean active, Boolean banned,
            int page, int limit, String sortBy, String sortOrder) {
        this(search, role, active, banned, page, limit, sortBy, sortOrder, false);
    }


    public AccountQueryDTO {
        if (page < 1) {
            page = 1;
        }
        if (limit < 1) {
            limit = 10;
        } else if (limit > 100) {
            limit = 100;
        }
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "joinedAt";
        }
        if (sortOrder == null || sortOrder.isBlank()) {
            sortOrder = "desc";
        }
    }
}
