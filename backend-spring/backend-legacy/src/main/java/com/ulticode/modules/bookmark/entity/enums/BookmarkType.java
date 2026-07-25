package com.ulticode.modules.bookmark.entity.enums;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enum for bookmark target types.
 * Matches Prisma schema BookmarkType enum.
 */
public enum BookmarkType {
    PROBLEM,
    SOLUTION,
    FORUM_POST,
    PROBLEM_LIST,
    SOLUTION_COMMENT,
    FORUM_COMMENT;

    /**
     * Leaf target types — concrete entities a user can bookmark, as
     * opposed to {@link #PROBLEM_LIST} which is a container/folder of
     * problems. Used by downstream modules (e.g. edge-operations
     * {@code getFavoritesCount}) that aggregate bookmark counts per
     * target. When adding a new leaf BookmarkType (e.g. a new comment
     * category), this set must be updated.
     */
    public static Set<BookmarkType> leafTypes() {
        return Set.of(PROBLEM, SOLUTION, FORUM_POST, SOLUTION_COMMENT, FORUM_COMMENT);
    }

    /**
     * Convenience: leaf type names as strings, suitable for
     * {@code QueryWrapper.eq("target_type", name)} filters.
     */
    public static Set<String> leafTypeNames() {
        return leafTypes().stream()
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
