package com.ulticode.submission.api.service;

import com.ulticode.submission.api.dto.LanguageCountDTO;
import com.ulticode.submission.api.dto.StatusCountDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardChartDataDTO;
import com.ulticode.submission.api.dto.SubmissionDashboardStatsDTO;
import com.ulticode.submission.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.submission.api.dto.SubmissionAdminRowDTO;
import com.ulticode.common.response.PageResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Owner read seam through which the Admin BFF obtains Submission list /
 * detail / statistics data without importing the App-private submission
 * entities, mappers, services or internal DTOs.
 *
 * <p>Provider lives in {@code backend-submission} and executes every query
 * inside the Submission owner; the Admin consumer depends only on this
 * entity-free contract. During the reversible read-route window an App
 * compatibility provider may remain registered separately. Pagination, filter and sort
 * semantics mirror the legacy admin mapper adapter exactly (including
 * the problem-title search pre-fetch and the {@code createdAt} default
 * sort). Analytics (status / language counts) are part of this admin
 * read seam; the range counters here serve the admin dashboard.
 *
 * <p>Non-throwing contract: single-row lookups return {@code null} for a
 * missing row (the Admin edge maps to its own error semantics); list and
 * count reads never return {@code null}.
 */
public interface SubmissionAdminReadPort {

    /** Count submissions grouped by status. */
    List<StatusCountDTO> countByStatus();

    /** Count submissions grouped by language. */
    List<LanguageCountDTO> countByLanguage();

    /**
     * Full submission row (detail shape: code, notes, percentiles, test
     * details, distribution bins) by id; {@code null} when the row is
     * missing.
     */
    SubmissionAdminRowDTO findById(String id);

    /**
     * Paginated submission search with the legacy admin filter/sort
     * semantics; never {@code null}.
     */
    PageResult<SubmissionAdminRowDTO> search(SubmissionAdminQueryDTO query, int page, int pageSize);

    /** Count all submissions across the system. */
    long countAll();

    /** Submissions created at or after {@code from}. */
    long countCreatedSince(LocalDateTime from);

    /** Submissions currently in {@code status}. */
    long countByStatus(String status);

    /** Distinct submission language codes observed in the store. */
    List<String> findDistinctLanguages();

    /** Distinct submitters with at least one submission in {@code [from, to)}. */
    long countDistinctUsersInRange(LocalDateTime from, LocalDateTime to);

    /** Submissions created at or after {@code from}. */
    long countSubmissionsInRange(LocalDateTime from);

    /** Accepted submissions created at or after {@code from}. */
    long countAcceptedSubmissionsInRange(LocalDateTime from);

    /** Load the bounded submission aggregates used by the Admin Dashboard. */
    SubmissionDashboardStatsDTO loadDashboardStats(LocalDateTime now);

    /** Load date buckets for the bounded Admin Dashboard submission chart. */
    List<SubmissionDashboardChartDataDTO> loadDashboardChartData(
            LocalDateTime start, LocalDateTime end, String period);
}
