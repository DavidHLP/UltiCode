package com.ulticode.modules.vote.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Enum for edge operation types.
 * Matches Prisma schema EdgeOperationType enum.
 */
public enum EdgeOperationType {
    VOTE_UP("VOTE_UP"),
    VOTE_DOWN("VOTE_DOWN"),
    ANALYZE("ANALYZE"),
    VIEW("VIEW"),
    // D-10 per-problem reactions. Stored as rows in edge_operations; consumed
    // by the ProblemReactionType UI (a "like" | "dislike" alias backed by
    // LIKE/DISLIKE) once the D-10 feature ships. Until then, these values
    // are accepted on POST /edge-operations but the response VO does NOT
    // surface a per-user flag for them — they take the silent-toggle path
    // in EdgeOperationsServiceImpl#toggleOperation (see
    // docs/edge-operations-api-test-report-2026-06-11.md §四 / §六).
    // The toggle is also the path for VIEW/ANALYZE/FAVORITE.
    LIKE("LIKE"),
    DISLIKE("DISLIKE"),
    FAVORITE("FAVORITE");

    @EnumValue
    private final String value;

    EdgeOperationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
