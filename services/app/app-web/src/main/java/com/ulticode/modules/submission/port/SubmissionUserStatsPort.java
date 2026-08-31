package com.ulticode.modules.submission.port;

import com.ulticode.submission.api.dto.SubmissionDateCountDTO;
import com.ulticode.common.dto.DifficultyCountDTO;

import java.util.List;

/**
 * Typed read port for per-user submission statistics.
 *
 * <p>The submission module owns this port. Cross-module consumers
 * (achievement, user, and admin projections) depend on it instead of
 * reaching into {@code SubmissionMapper} directly, keeping the submission
 * persistence implementation behind the module boundary.
 */
public interface SubmissionUserStatsPort {

    /** Distinct accepted problems solved by the user. */
    Long countAcceptedProblemsByUserId(String userId);

    /** Total submissions made by the user. */
    Long countByUserId(String userId);

    /** Total submissions by the user (alias used by profile stats). */
    Long countTotalSubmissionsByUserId(String userId);

    /** Acceptance rate (0–100) for the user, or {@code null} when unknown. */
    Double calculateAcceptanceRateByUserId(String userId);

    /** Global rank by accepted-submission count, or {@code null} when unranked. */
    Integer findGlobalRankByUserId(String userId);

    /** Accepted-problem counts grouped by difficulty for the user. */
    List<DifficultyCountDTO> countAcceptedProblemsByDifficulty(String userId);

    /** Daily submission counts for the user within the given calendar year. */
    List<SubmissionDateCountDTO> findSubmissionCountsByDate(String userId, Integer year);
}
