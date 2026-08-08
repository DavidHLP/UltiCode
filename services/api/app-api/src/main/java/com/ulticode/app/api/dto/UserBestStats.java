package com.ulticode.app.api.dto;

import java.io.Serializable;

/**
 * DTO capturing per-user best (fastest / lowest-memory) accepted submission
 * metrics, aggregated server-side by
 * {@link com.ulticode.modules.submission.mapper.SubmissionMapper#findBestStatsByProblemAndLanguage}.
 *
 * <p>Both numeric fields are nullable because the SQL aggregate {@code MIN(...)}
 * returns {@code NULL} when a user has no accepted submissions of a given
 * problem/language combination. Callers must filter or guard accordingly.
 *
 * @param userId        the user identifier (never null in practice; the
 *                      underlying query filters out NULL user_ids)
 * @param bestRuntimeMs minimum runtime in milliseconds across the user's
 *                      accepted submissions, or null if none
 * @param bestMemoryMb  minimum memory usage in MB across the user's accepted
 *                      submissions, or null if none
 */
public record UserBestStats(
        String userId,
        Integer bestRuntimeMs,
        Double bestMemoryMb
) implements Serializable {
}
