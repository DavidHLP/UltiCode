package com.ulticode.modules.submission.projection;

import com.ulticode.submission.api.dto.LearningProgressDTO;
import com.ulticode.submission.api.dto.PerformanceStats;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import com.ulticode.submission.api.dto.SubmissionVO;
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
     * Page overload: enrich problem display facts from a pre-fetched batch
     * map and batch user summaries once for the whole page.
     */
    SubmissionVO toVO(Submission submission, Map<Long, com.ulticode.app.api.service.ProblemFactsPort.ProblemDisplayFacts> batchFacts);

    List<SubmissionVO> toVO(
            List<Submission> submissions,
            Map<Long, com.ulticode.app.api.service.ProblemFactsPort.ProblemDisplayFacts> batchFacts);

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
