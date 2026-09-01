package com.ulticode.modules.submission.projection;

import com.ulticode.submission.api.dto.LearningProgressDTO;
import com.ulticode.submission.api.dto.PerformanceStats;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionHistoryDTO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionStatusMeta;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;

import java.util.List;
import java.util.Map;

/**
 * Projection seam owned by {@code backend-submission}.
 *
 * <p>The P0-1 security projection and the user-facing read aggregations
 * (calendar dates, learning progress, submission history, status catalog) are
 * owned here and read only the {@code submissions} table. User/problem
 * summaries cross the explicit App-owned seams; no cross-owner JOIN is used
 * (DEC-011).
 */
public interface SubmissionProjection {

    /**
     * Convert a submission to the lightweight problem-list item shape.
     */
    SubmissionListItemVO toListItemVO(
            Submission submission,
            com.ulticode.app.api.service.ProblemFactsPort.ProblemDisplayFacts problemFacts);

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

    /** Detail projection after a bounded problem-facts batch. */
    SubmissionDetailVO toDetailVO(
            Submission submission,
            PerformanceStats stats,
            Map<Long, com.ulticode.app.api.service.ProblemFactsPort.ProblemDisplayFacts> batchFacts);

    /** Aggregate the {@code YYYY-MM-DD} dates for {@code userId} in the year. */
    List<String> aggregateDates(String userId, Integer year);

    /** Aggregate weekly solved counts, time spent, and streak for {@code userId}. */
    LearningProgressDTO aggregateLearningProgress(String userId);

    /** Aggregate monthly counts, language breakdown, and acceptance for {@code userId}. */
    SubmissionHistoryDTO aggregateHistory(String userId);

    /** Project the canonical status catalog for {@code /submissions/statuses}. */
    List<SubmissionStatusMeta> getStatusCatalog();
}
