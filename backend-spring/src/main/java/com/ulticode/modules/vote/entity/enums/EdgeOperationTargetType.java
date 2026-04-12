package com.ulticode.modules.vote.entity.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Enum for edge operation target types.
 * Matches Prisma schema EdgeOperationTargetType enum.
 */
public enum EdgeOperationTargetType {
    PROBLEM("PROBLEM"),
    SOLUTION("SOLUTION"),
    POST("POST"),
    COMMENT("COMMENT"),
    FORUM_POST("FORUM_POST"),
    FORUM_COMMENT("FORUM_COMMENT"),
    SOLUTION_COMMENT("SOLUTION_COMMENT"),
    PROBLEM_LIST("PROBLEM_LIST");

    @EnumValue
    private final String value;

    EdgeOperationTargetType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
