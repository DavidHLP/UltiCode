package com.ulticode.auth.api.dto;

import java.io.Serializable;

/** Entity-free date bucket returned by the Auth-owned user trend aggregate. */
public record AuthUserTrendBucketDTO(String date, long count) implements Serializable {
    private static final long serialVersionUID = 1L;

    public AuthUserTrendBucketDTO {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("date must not be blank");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }
}
