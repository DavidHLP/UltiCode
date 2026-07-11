package com.ulticode.modules.admin.port;

import java.time.LocalDateTime;

/**
 * Admin-owned projection of a contest row containing only the primitive
 * fields the analytics reporters consume.
 *
 * <p>Replaces the {@code List<Contest>} leak in
 * {@link AdminAnalyticsPort#loadContestData}: the contest module no longer
 * has to share its mutable entity with the admin module, and the admin
 * reporters cannot accidentally depend on additional {@code Contest}
 * fields that have no analytics meaning.
 *
 * @author ulticode
 */
public record ContestSummary(
        String id,
        String title,
        String contestType,
        LocalDateTime startTime
) {}