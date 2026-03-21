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
    COMMENT("COMMENT");

    @EnumValue
    private final String value;

    EdgeOperationTargetType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
