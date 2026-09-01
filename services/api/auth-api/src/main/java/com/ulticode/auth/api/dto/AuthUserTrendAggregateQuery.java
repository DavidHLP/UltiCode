package com.ulticode.auth.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Bounded query for Auth-owned user-registration trend buckets.
 *
 * <p>The provider validates the range and bucket limit before touching the
 * Auth store. Dates are local database timestamps, matching the existing
 * dashboard chart contracts.</p>
 */
public record AuthUserTrendAggregateQuery(
        LocalDateTime start,
        LocalDateTime end,
        String period,
        int maxBuckets) implements Serializable {

    /** Hard cap keeps the largest supported dashboard window finite. */
    public static final int MAX_BUCKETS = 10_000;

    private static final long serialVersionUID = 1L;

    public AuthUserTrendAggregateQuery {
        period = period == null ? null : period.trim().toLowerCase(Locale.ROOT);
    }
}
