package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.ProblemAdminCasesDTO;
import com.ulticode.app.api.dto.ProblemAdminCodeDTO;
import com.ulticode.app.api.dto.ProblemAdminDescriptionDTO;
import com.ulticode.app.api.dto.ProblemAdminQueryDTO;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.dto.ProblemAdminTagDTO;
import com.ulticode.app.api.dto.ProblemAdminTestCaseDTO;
import com.ulticode.common.response.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * Owner read seam through which the Admin BFF obtains Problem / TestCase /
 * Tag / Detail / Export data without importing the App-private problem
 * entities, mappers, services or internal DTOs.
 *
 * <p>Provider lives in {@code backend-app} (Problem module) and executes
 * every query inside the App owner; the Admin consumer depends only on this
 * entity-free contract. Each operation is a single bounded RPC — composed
 * tab payloads ({@link #findDescription}/{@link #findCode}/{@link #findCases})
 * and batch reads ({@link #listProblems}/{@link #listAllProblems}/
 * {@link #findProblemsByIds}/{@link #findBySlugs}) deliberately avoid per-row
 * N+1 fan-out.
 *
 * <p>Non-throwing contract: single-row lookups return {@code null} for a
 * missing row (the Admin edge maps to its own error semantics); list reads
 * never return {@code null}.
 */
public interface ProblemAdminReadPort {

    // ── Problem rows ────────────────────────────────────────────

    /**
     * Full problem row by id; {@code null} when the row is missing.
     * Tags are left empty (matching the legacy single-row VO conversion).
     */
    ProblemAdminRowDTO findProblem(Long id);

    /**
     * Full problem row by slug; {@code null} when the row is missing.
     */
    ProblemAdminRowDTO findBySlug(String slug);

    /**
     * Batch problem rows by slug. The provider accepts at most 500 slugs,
     * returns only found rows, and does not guarantee result order.
     */
    List<ProblemAdminRowDTO> findBySlugs(Collection<String> slugs);

    /**
     * Batch problem rows by ids, tag-enriched. Absent ids are simply not
     * present in the result; the list is never null.
     */
    List<ProblemAdminRowDTO> findProblemsByIds(Collection<Long> ids);

    // ── Admin tab payloads ──────────────────────────────────────

    /**
     * Description-tab payload (problem + detail + tags + examples).
     * {@code null} when the problem row is missing.
     */
    ProblemAdminDescriptionDTO findDescription(Long problemId);

    /**
     * Code-tab payload (problem + starter-code languages).
     * {@code null} when the problem row is missing.
     */
    ProblemAdminCodeDTO findCode(Long problemId);

    /**
     * Cases-tab payload (problem + examples + detail constraints/hints +
     * tags). {@code null} when the problem row is missing.
     */
    ProblemAdminCasesDTO findCases(Long problemId);

    // ── List / export / moderation reads ────────────────────────

    /**
     * Paginated problem list with filters; never null.
     */
    PageResult<ProblemAdminRowDTO> listProblems(ProblemAdminQueryDTO query);

    /**
     * Un-paginated problem list for export; never null.
     */
    List<ProblemAdminRowDTO> listAllProblems(ProblemAdminQueryDTO query);

    /**
     * Paginated flagged-problem list (raw SQL view incl. soft-deleted rows,
     * matching the legacy mapper pair); never null.
     */
    PageResult<ProblemAdminRowDTO> listFlaggedProblems(String status, int page, int limit);

    /**
     * Problem ids whose title contains the given text. Preserves the legacy
     * submission-search pre-fetch semantics (all matches, no limit).
     */
    List<Long> searchProblemIdsByTitle(String title);

    // ── Test cases ──────────────────────────────────────────────

    /**
     * Paginated test cases for a problem, in {@code test_order} asc order,
     * optionally filtered by sample/hidden scope; never null.
     */
    PageResult<ProblemAdminTestCaseDTO> listTestCases(
            Long problemId, Boolean isSample, Boolean isHidden, int page, int limit);

    /**
     * Single test case by id, bound to the owning problem; {@code null}
     * when missing or owned by a different problem.
     */
    ProblemAdminTestCaseDTO getTestCase(Long problemId, String testCaseId);

    /**
     * Batch test cases for a problem. The provider accepts at most 1000 IDs
     * (the Admin reorder endpoint's request cap), returns only rows owned by
     * {@code problemId}, and never returns {@code null}. Result order is not
     * significant.
     */
    List<ProblemAdminTestCaseDTO> findTestCasesByIds(
            Long problemId, Collection<String> testCaseIds);

    /**
     * All test cases for a problem in {@code test_order} asc order; never null.
     */
    List<ProblemAdminTestCaseDTO> exportTestCases(Long problemId);

    // ── Tags ────────────────────────────────────────────────────

    /**
     * Paginated problem-tag list with search and sort; never null.
     */
    PageResult<ProblemAdminTagDTO> listTags(
            String search, int pageNum, int pageSize, String sortBy, String sortOrder);

    /**
     * Single problem tag by id; {@code null} when missing.
     */
    ProblemAdminTagDTO getTagById(String id);

    /**
     * Whether a tag with the exact label already exists.
     */
    boolean tagNameExists(String name);

    /**
     * Whether a tag with the exact slug already exists.
     */
    boolean tagSlugExists(String slug);
}
