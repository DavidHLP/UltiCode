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
    VIEW("VIEW");

    @EnumValue
    private final String value;

    EdgeOperationType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
