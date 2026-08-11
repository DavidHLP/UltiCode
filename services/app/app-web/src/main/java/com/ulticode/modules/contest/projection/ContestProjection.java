package com.ulticode.modules.contest.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.ContestListVO;
import com.ulticode.modules.contest.dto.ContestProblemVO;
import com.ulticode.modules.contest.dto.ContestQueryDTO;
import com.ulticode.modules.contest.dto.ContestRankingVO;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.GlobalContestStatsVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.app.api.dto.SubmissionVO;

import java.util.List;
import java.util.Optional;

/**
 * Read-side projection for the contest domain — a deep module that owns every
 * entity-to-VO projection rule, list-query builder, statistics aggregation and
 * ranking read.
 *
 * <p>This is the same shallow cluster that was lifted out of
 * {@link com.ulticode.modules.contest.service.ContestService} for moderation,
 * submission and problem: entity-to-VO projections, five list-query builders
 * ({@link #findAllListVO} / {@link #findAllAdmin} with their shared sort-default
 * policy and {@link #findUpcoming} / {@link #findRunning} / {@link #findPast}
 * with their shared batch-enrichment step), one global stats aggregation
 * ({@link #getStats}) and four ranking reads (the cached
 * {@link #getGlobalRanking} / {@link #getContestRanking} /
 * {@link #getGlobalRankingsPaginated} and the admin
 * {@link #getAdminContestRanking}). Sitting next to the contest state machine
 * (create / update / delete / start / end / addProblem / removeProblem /
 * submit) made every projection tweak land in the same file as the write paths.
 *
 * <p>After the deepening:
 * <ul>
 *   <li>{@link com.ulticode.modules.contest.service.ContestService} keeps the
 *       write state machine plus the participation lifecycle methods that
 *       delegate to the scheduler. Write paths return their view shapes through
 *       {@link #toVO(Contest, String)}.</li>
 *   <li>Controllers depend on this projection directly for reads and on the
 *       service for writes.</li>
 * </ul>
 *
 * <p>All methods are pure reads; none mutate contest state. Single-item
 * endpoints throw {@link com.ulticode.common.exception.ErrorCode#CONTEST_NOT_FOUND}
 * when the contest is missing or soft-deleted so callers receive consistent
 * semantics across the catalog, admin and submission-bridge paths.
 *
 * @author ulticode
 */
public interface ContestProjection {

    /**
     * Find a contest by its database id (internal / id-resolution use).
     *
     * @param id the contest id
     * @return the contest if found, empty otherwise
     */
    Optional<Contest> findById(String id);

    /**
     * Find a contest by its slug (internal / id-resolution use).
     *
     * @param slug the contest slug
     * @return the contest if found, empty otherwise
     */
    Optional<Contest> findBySlug(String slug);

    /**
     * Get a contest by id-or-slug, projected to a {@link ContestVO}. Resolves
     * the identifier as id first, then slug, throwing 404 when neither matches.
     *
     * @param idOrSlug the contest id or slug
     * @param userId   the current user id (optional, for participation enrichment)
     * @return the contest view object
     */
    ContestVO getContestById(String idOrSlug, String userId);

    /**
     * Get a contest by id-or-slug for the public catalog. Invisible contests
     * are reported as not found; admin reads use {@link #getContestById}.
     *
     * @param idOrSlug the contest id or slug
     * @param userId   the current user id (optional, for participation enrichment)
     * @return the publicly visible contest view object
     */
    ContestVO getPublicContestById(String idOrSlug, String userId);

    /**
     * Project a {@link Contest} entity into a {@link ContestVO}. Exposed so the
     * write-side service can shape its return values without re-implementing the
     * projection rules (problem-count lookup, participation enrichment, derived
     * fields).
     *
     * @param contest the contest entity
     * @param userId  the current user id (optional, for participation enrichment)
     * @return the contest view object, or {@code null} if the input is {@code null}
     */
    ContestVO toVO(Contest contest, String userId);

    /**
     * User-contest history: the contests a user has registered for, is
     * currently running, or has finished, filtered by the supplied
     * <code>type</code> token and projected through the same batched
     * ContestVO path as the public catalog. Replaces the previous
     * N+1 re-read loop in
     * {@link com.ulticode.modules.contest.service.ContestParticipationServiceImpl#getUserContests(String, String)}
     * that walked the user's participation rows and re-fetched each
     * contest individually, then re-read the same participant inside
     * the VO conversion.
     *
     * <p>Reads are pure: this method does not mutate contest state.
     * The implementation loads each Contest row exactly once via
     * {@code selectBatchIds} and reads each {@code ContestParticipant}
     * row exactly once via {@code findByUserId}.
     *
     * @param userId the current user id (required)
     * @param type   filter: {@code "registered"} for REGISTERED rows,
     *               {@code "virtual"} for any virtual session,
     *               anything else for FINISHED or STARTED real sessions
     * @return the projected ContestVO list (empty when the user has no
     *         matching participations)
     */
    java.util.List<ContestVO> findUserContests(String userId, String type);

    /**
     * Get the problems for a contest, projected to {@link ContestProblemVO}.
     *
     * @param contestId the contest id
     * @return the contest problem view objects (with title / slug / difficulty enrichment)
     */
    List<ContestProblemVO> getContestProblems(String contestId);

    /**
     * Get the current user's submissions for a contest problem.
     *
     * @param contestId the contest id
     * @param problemId the underlying numeric problem id
     * @param userId    the current user id
     * @return the user's submission view objects for that contest problem
     */
    List<SubmissionVO> getContestProblemSubmissions(String contestId, Long problemId, String userId);

    /**
     * Get the announcements for a contest, newest first.
     *
     * @param contestId the contest id
     * @return the contest announcements
     */
    List<ContestAnnouncement> getContestAnnouncements(String contestId);

    /**
     * Resolve a path-variable problem identifier into the underlying numeric
     * problem id. Accepts either a numeric id (e.g. "1") or the composite
     * contest_problem id (e.g. "cp-u1-A"). Throws 404 with a clear message if
     * neither resolves.
     *
     * @param contestId   the resolved contest id
     * @param problemPath the raw path value (numeric or composite)
     * @return the numeric problem id
     */
    Long resolveContestProblemId(String contestId, String problemPath);

    /**
     * User-facing contest list (excludes drafts and invisible contests).
     *
     * @param query  the query parameters (page, pageSize, status, type, rated, search, sort)
     * @param userId the current user id (optional, for participation enrichment)
     * @return paginated list of contest list view objects
     */
    PageResult<ContestListVO> findAllListVO(ContestQueryDTO query, String userId);

    /**
     * Admin contest list (includes drafts and invisible contests).
     *
     * @param query  the query parameters
     * @param userId the current user id (optional)
     * @return paginated list of contest list view objects
     */
    PageResult<ContestListVO> findAllAdmin(ContestQueryDTO query, String userId);

    /**
     * Upcoming contests with default pagination (page 1, size 20).
     *
     * @param userId the current user id (optional, for participation enrichment)
     * @return paginated list of upcoming contest list view objects
     */
    PageResult<ContestListVO> findUpcoming(String userId);

    /**
     * Upcoming contests with explicit pagination.
     *
     * @param userId   the current user id (optional, for participation enrichment)
     * @param page     the page number (1-based)
     * @param pageSize the page size (clamped to [1, 50])
     * @return paginated list of upcoming contest list view objects
     */
    PageResult<ContestListVO> findUpcoming(String userId, int page, int pageSize);

    /**
     * Running contests with default pagination (page 1, size 20).
     *
     * @param userId the current user id (optional, for participation enrichment)
     * @return paginated list of running contest list view objects
     */
    PageResult<ContestListVO> findRunning(String userId);

    /**
     * Running contests with explicit pagination.
     *
     * @param userId   the current user id (optional, for participation enrichment)
     * @param page     the page number (1-based)
     * @param pageSize the page size (clamped to [1, 50])
     * @return paginated list of running contest list view objects
     */
    PageResult<ContestListVO> findRunning(String userId, int page, int pageSize);

    /**
     * Past (finished) contests with pagination.
     *
     * @param page     the page number (1-based, null defaults to 1)
     * @param pageSize the page size (null / non-positive defaults to 10, clamped to 50)
     * @param userId   the current user id (optional, for participation enrichment)
     * @return paginated list of past contest list view objects
     */
    PageResult<ContestListVO> findPast(Integer page, Integer pageSize, String userId);

    /**
     * Global contest statistics (registered / active / completed participants,
     * total submissions).
     *
     * @return the global contest statistics
     */
    GlobalContestStatsVO getStats();

    /**
     * Global top ranking (cached). {@code limit} is clamped to [1, 100],
     * defaulting to 10 when null / non-positive.
     *
     * @param limit the maximum number of rankings to return
     * @return list of global ranking view objects
     */
    List<ContestRankingVO> getGlobalRanking(Integer limit);

    /**
     * Global ranking with pagination and optional country filter (cached).
     *
     * @param page    the page number (1-based, null defaults to 1)
     * @param limit   the page size (clamped to [1, 100], default 50)
     * @param country optional country filter (ISO code or free-text match); null/blank = global
     * @return paginated list of global ranking view objects
     */
    PageResult<ContestRankingVO> getGlobalRankingsPaginated(Integer page, Integer limit, String country);

    /**
     * Per-contest ranking with keyset cursor pagination (cached). The cache key
     * includes {@code contestId} so per-contest eviction is possible. The cursor
     * format is "{@code rank:userId}"; null/blank means first page.
     *
     * @param contestId the contest id; null/blank falls back to the global ranking
     * @param limit     the maximum number of rankings to return (clamped to [1, 100], default 10)
     * @param cursor    the keyset cursor ({@code rank:userId}), or null/blank for the first page
     * @return list of contest ranking view objects
     */
    List<ContestRankingVO> getContestRanking(String contestId, Integer limit, String cursor);

    /**
     * Admin per-contest ranking (paginated). Throws 404 when the contest does
     * not exist or is soft-deleted.
     *
     * @param contestId the contest id
     * @param page      the page number (1-based)
     * @param limit     the number of items per page
     * @return paginated list of contest ranking view objects
     */
    PageResult<ContestRankingVO> getAdminContestRanking(String contestId, Integer page, Integer limit);
}
