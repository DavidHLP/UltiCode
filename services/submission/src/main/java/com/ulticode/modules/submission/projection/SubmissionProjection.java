package com.ulticode.modules.submission.projection;

import com.ulticode.app.api.dto.LearningProgressDTO;
import com.ulticode.app.api.dto.PerformanceStats;
import com.ulticode.app.api.dto.SubmissionDetailVO;
import com.ulticode.app.api.dto.SubmissionHistoryDTO;
import com.ulticode.app.api.dto.SubmissionStatusMeta;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;

import java.util.List;
import java.util.Map;

/**
 * Projection seam owned by {@code backend-submission}.
 *
 * <p>SPLIT-003 slice-2 copies the write-path projection ({@link #toVO})
 * with its P0-1 security filtering. SPLIT-004 slice-7 adds the user-facing
 * read aggregations (calendar dates, learning progress, submission history,
 * status catalog) that are pure {@code submissions}-table reads — no
 * cross-owner JOIN, per DEC-011. The pre-joined list-item overloads
 * ({@code toListItemVO} / {@code toVO(SubmissionWithProblem)}) stay in App
 * until the read-routing cutover slice.
 */
public interface SubmissionProjection {

    /**
     * Convert a submission entity to its user-facing VO, applying the P0-1
     * hidden-case filter and first-failure extraction. Never {@code null}.
     */
    SubmissionVO toVO(Submission submission);

    /**
     * List overload: enrich problem display facts from a pre-fetched batch
     * map instead of one lookup per row (SPLIT-004 slice-8). Rows whose
     * problem id is absent from {@code batchFacts} fall back to the single
     * lookup seam, matching {@link #toVO(Submission)} semantics.
     */
    SubmissionVO toVO(Submission submission, Map<Long, com.ulticode.app.api.service.ProblemFactsPort.ProblemDisplayFacts> batchFacts);

    /**
     * Convert a submission entity to its full user-facing detail VO with
     * performance stats, applying the same P0-1 filter and enrichment as
     * App's {@code toDetailVO}. Never {@code null}.
     */
    SubmissionDetailVO toDetailVO(Submission submission, PerformanceStats stats);

    /** Aggregate the {@code YYYY-MM-DD} dates for {@code userId} in the year. */
    List<String> aggregateDates(String userId, Integer year);

    /** Aggregate weekly solved counts, time spent, and streak for {@code userId}. */
    LearningProgressDTO aggregateLearningProgress(String userId);

    /** Aggregate monthly counts, language breakdown, and acceptance for {@code userId}. */
    SubmissionHistoryDTO aggregateHistory(String userId);

    /** Project the canonical status catalog for {@code /submissions/statuses}. */
    List<SubmissionStatusMeta> getStatusCatalog();
}
