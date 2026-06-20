package com.ulticode.modules.contest.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.service.ContestService;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import java.util.Optional;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * REST controller for contest-related operations.
 */
@Tag(name = "Contest", description = "Contest management endpoints")
@RestController
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestController {

    private final ContestService contestService;
    private final RankingService rankingService;
    private final Validator validator;
    private final ContestProblemMapper contestProblemMapper;

    /** Error messages — kept as constants for testability and i18n-future-readiness. */
    private static final String MSG_PROBLEM_ID_REQUIRED = "Problem id is required";

    // =========================================================================
    // CONTEST QUERIES (Public)
    // =========================================================================

    /**
     * Get contest list with pagination and filters.
     * Public endpoint - accessible without authentication.
     *
     * @param page     the page number (1-based)
     * @param pageSize the number of items per page
     * @param status   filter by status
     * @param search   search by ID, title, or slug
     * @param sort     sort field
     * @param direction sort direction
     * @return paginated list of contests
     */
    @Operation(summary = "Get contest list", description = "Get a paginated list of contests with optional filters")
    @ApiResponse(responseCode = "200", description = "Contests retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @GetMapping
    public Result<PageResult<ContestListVO>> getContestList(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) String status,
            @Parameter(description = "Search by ID, title, or slug")
            @RequestParam(required = false) String search,
            @Parameter(description = "Sort field")
            @RequestParam(required = false) String sort,
            @Parameter(description = "Sort direction")
            @RequestParam(required = false) String direction,
            @Parameter(description = "Filter by contest type")
            @RequestParam(required = false) String contestType,
            @Parameter(description = "Filter by rated status")
            @RequestParam(required = false) Boolean isRated) {

        ContestQueryDTO query = new ContestQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setStatus(status);
        query.setSearch(search);
        query.setSort(sort);
        query.setDirection(direction);
        query.setContestType(contestType);
        query.setIsRated(isRated);

        // Get optional userId for user-specific fields
        String userId = SecurityUtil.getCurrentUserId();
        PageResult<ContestListVO> result = contestService.findAllListVO(query, userId);
        return Result.success(result);
    }

    /**
     * Get upcoming contests.
     * Public endpoint - accessible without authentication.
     *
     * @return list of upcoming contests
     */
    @Operation(summary = "Get upcoming contests", description = "Get a paginated list of upcoming contests")
    @ApiResponse(responseCode = "200", description = "Upcoming contests retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @GetMapping("/upcoming")
    public Result<PageResult<ContestListVO>> getUpcomingContests() {
        String userId = SecurityUtil.getCurrentUserId();
        PageResult<ContestListVO> contests = contestService.findUpcoming(userId);
        return Result.success(contests);
    }

    /**
     * Get running contests.
     * Public endpoint - accessible without authentication.
     *
     * @return list of running contests
     */
    @Operation(summary = "Get running contests", description = "Get a paginated list of currently running contests")
    @ApiResponse(responseCode = "200", description = "Running contests retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @GetMapping("/running")
    public Result<PageResult<ContestListVO>> getRunningContests() {
        String userId = SecurityUtil.getCurrentUserId();
        PageResult<ContestListVO> contests = contestService.findRunning(userId);
        return Result.success(contests);
    }

    /**
     * Get past contests with pagination.
     * Public endpoint - accessible without authentication.
     *
     * @param page     the page number (1-based)
     * @param pageSize the number of items per page
     * @return paginated list of past contests
     */
    @Operation(summary = "Get past contests", description = "Get a paginated list of past contests")
    @ApiResponse(responseCode = "200", description = "Past contests retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @GetMapping("/past")
    public Result<PageResult<ContestListVO>> getPastContests(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {

        String userId = SecurityUtil.getCurrentUserId();
        PageResult<ContestListVO> result = contestService.findPast(page, pageSize, userId);
        return Result.success(result);
    }

    /**
     * Get contest statistics.
     * Public endpoint - accessible without authentication.
     *
     * @return contest statistics
     */
    @Operation(summary = "Get contest statistics", description = "Get overall contest statistics")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved", content = @Content(schema = @Schema(implementation = GlobalContestStatsVO.class)))
    @GetMapping("/stats")
    public Result<GlobalContestStatsVO> getContestStats() {
        GlobalContestStatsVO stats = contestService.getStats();
        return Result.success(stats);
    }

    /**
     * Get global ranking.
     * Public endpoint - accessible without authentication.
     *
     * @param limit the maximum number of rankings to return
     * @return list of global rankings
     */
    @Operation(summary = "Get global ranking", description = "Get the global leaderboard")
    @ApiResponse(responseCode = "200", description = "Global ranking retrieved", content = @Content(schema = @Schema(implementation = java.util.List.class)))
    @GetMapping("/global-ranking")
    public Result<List<ContestRankingVO>> getGlobalRanking(
            @Parameter(description = "Maximum number of rankings to return")
            @RequestParam(required = false, defaultValue = "10") Integer limit) {

        List<ContestRankingVO> rankings = contestService.getGlobalRanking(limit);
        return Result.success(rankings);
    }

    /**
     * Get global rankings with pagination.
     * Public endpoint - accessible without authentication.
     *
     * @param page    the page number (1-based)
     * @param limit   the number of items per page
     * @param country optional country filter (matches against global_rankings.country);
     *                null/blank returns the unfiltered global leaderboard
     * @return paginated list of global rankings
     */
    @Operation(summary = "Get global rankings with pagination", description = "Get paginated global leaderboard")
    @ApiResponse(responseCode = "200", description = "Global rankings retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @GetMapping("/rankings/global")
    public Result<PageResult<ContestRankingVO>> getGlobalRankingsPaginated(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @Parameter(description = "Country filter (matches global_rankings.country)")
            @RequestParam(required = false) String country) {

        PageResult<ContestRankingVO> result = contestService.getGlobalRankingsPaginated(page, limit, country);
        return Result.success(result);
    }

    /**
     * Get contest details by ID.
     * Public endpoint - accessible without authentication.
     *
     * @param id the contest ID
     * @return the contest details
     */
    @Operation(summary = "Get contest by ID", description = "Get a contest's details by its ID")
    @ApiResponse(responseCode = "200", description = "Contest retrieved", content = @Content(schema = @Schema(implementation = ContestVO.class)))
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}")
    public Result<ContestVO> getContestById(
            @Parameter(description = "Contest ID")
            @PathVariable String id) {

        String resolvedId = resolveContestId(id);
        // Get optional userId for user-specific fields
        String userId = SecurityUtil.getCurrentUserId();
        ContestVO contest = contestService.getContestById(resolvedId, userId);
        return Result.success(contest);
    }

    /**
     * Get contest problems.
     * Public endpoint - accessible without authentication.
     *
     * @param id the contest ID
     * @return list of contest problems
     */
    @Operation(summary = "Get contest problems", description = "Get the problems for a specific contest")
    @ApiResponse(responseCode = "200", description = "Contest problems retrieved", content = @Content(schema = @Schema(implementation = java.util.List.class)))
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}/problems")
    public Result<List<ContestProblemVO>> getContestProblems(
            @Parameter(description = "Contest ID")
            @PathVariable String id) {

        String resolvedId = resolveContestId(id);
        List<ContestProblemVO> problems = contestService.getContestProblems(resolvedId);
        return Result.success(problems);
    }

    /**
     * Get contest announcements.
     * Public endpoint - accessible without authentication.
     *
     * @param id the contest ID
     * @return list of contest announcements
     */
    @Operation(summary = "Get contest announcements", description = "Get the announcements for a specific contest")
    @ApiResponse(responseCode = "200", description = "Contest announcements retrieved", content = @Content(schema = @Schema(implementation = java.util.List.class)))
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}/announcements")
    public Result<List<ContestAnnouncement>> getContestAnnouncements(
            @Parameter(description = "Contest ID")
            @PathVariable String id) {

        String resolvedId = resolveContestId(id);
        List<ContestAnnouncement> announcements = contestService.getContestAnnouncements(resolvedId);
        return Result.success(announcements);
    }

    /**
     * Get contest ranking.
     * Public endpoint - accessible without authentication.
     *
     * @param id    the contest ID
     * @param page  the page number (1-based)
     * @param limit the number of items per page
     * @return paginated list of rankings
     */
    @Operation(summary = "Get contest ranking", description = "Get the ranking for a specific contest")
    @ApiResponse(responseCode = "200", description = "Ranking retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}/ranking")
    public Result<PageResult<ContestRankingVO>> getContestRanking(
            @Parameter(description = "Contest ID")
            @PathVariable String id,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false, defaultValue = "50") Integer limit) {

        String resolvedId = resolveContestId(id);
        PageResult<ContestRankingVO> result = rankingService.getContestRanking(resolvedId, page, limit);
        return Result.success(result);
    }

    /**
     * Get live contest ranking.
     * Public endpoint - accessible without authentication.
     *
     * @param id    the contest ID
     * @param limit the maximum number of rankings to return
     * @return list of live rankings
     */
    @Operation(summary = "Get live ranking", description = "Get the live ranking for a running contest")
    @ApiResponse(responseCode = "200", description = "Live ranking retrieved", content = @Content(schema = @Schema(implementation = java.util.List.class)))
    @ApiResponse(responseCode = "403", description = "Contest is not currently running")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}/live-ranking")
    public Result<List<LiveRankingEntryVO>> getLiveRanking(
            @Parameter(description = "Contest ID")
            @PathVariable String id,
            @Parameter(description = "Maximum number of rankings to return")
            @RequestParam(required = false, defaultValue = "100") Integer limit) {

        String resolvedId = resolveContestId(id);
        List<LiveRankingEntryVO> rankings = rankingService.getLiveRanking(resolvedId, limit);
        return Result.success(rankings);
    }

    /**
     * Get the current user's submissions for a contest problem.
     * Requires authentication.
     *
     * @param id        the contest ID or slug
     * @param problemId the problem ID
     * @return list of submissions for the problem in this contest
     */
    @Operation(summary = "Get contest problem submissions", description = "Get the current user's submissions for a problem in a contest")
    @ApiResponse(responseCode = "200", description = "Contest problem submissions retrieved", content = @Content(schema = @Schema(implementation = java.util.List.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest or problem not found")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/{id}/problems/{problemId}/submissions")
    public Result<List<SubmissionVO>> getContestProblemSubmissions(
            @Parameter(description = "Contest ID or slug")
            @PathVariable String id,
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId) {

        String resolvedId = resolveContestId(id);
        String userId = getCurrentUserIdOrThrow();
        List<SubmissionVO> submissions = contestService.getContestProblemSubmissions(resolvedId, problemId, userId);
        return Result.success(submissions);
    }

    /**
     * Submit code for a contest problem.
     * Requires authentication.
     *
     * @param id          the contest ID or slug
     * @param problemPath the problem identifier — accepts both numeric problem id
     *                    (e.g., "1") and composite contest_problem id (e.g., "cp-u1-A").
     *                    Resolved to the underlying numeric problem id before delegation.
     * @param createDTO   the submission payload
     * @return created submission
     */
    @Operation(summary = "Submit contest problem", description = "Submit code for a problem in a contest")
    @ApiResponse(responseCode = "200", description = "Contest problem submitted", content = @Content(schema = @Schema(implementation = SubmissionVO.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest or problem not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:problem-submit", limit = 20, period = 60)
    @PostMapping("/{id}/problems/{problemId}/submissions")
    public Result<SubmissionVO> submitContestProblem(
            @Parameter(description = "Contest ID or slug")
            @PathVariable String id,
            // Path variable name kept as {problemId} for API compatibility;
            // Java parameter renamed to `problemPath` since it accepts both a
            // numeric id (e.g., "1") and a composite id (e.g., "cp-u1-A").
            @Parameter(description = "Problem identifier (numeric id or contest_problem id)")
            @PathVariable("problemId") String problemPath,
            @RequestBody CreateSubmissionDTO createDTO) {

        String resolvedId = resolveContestId(id);
        String userId = getCurrentUserIdOrThrow();
        if (createDTO == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Submission payload is required");
        }
        Long realProblemId = resolveContestProblemId(resolvedId, problemPath);
        createDTO.setProblemId(realProblemId);
        validateSubmissionPayload(createDTO);
        SubmissionVO submission = contestService.submitContestProblem(resolvedId, realProblemId, userId, createDTO);
        return Result.success(submission);
    }

    /**
     * Resolve a path-variable problem identifier into the underlying numeric problem id.
     * Accepts either a numeric id (e.g., "1") or the composite contest_problem id
     * (e.g., "cp-u1-A"). Throws 404 with a clear message if neither resolves.
     */
    private Long resolveContestProblemId(String contestId, String problemPath) {
        if (problemPath == null || problemPath.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, MSG_PROBLEM_ID_REQUIRED);
        }
        // 1) Try parsing as numeric (legacy & most common case).
        try {
            return Long.parseLong(problemPath);
        } catch (NumberFormatException ignored) {
            // fall through to contest_problem.id lookup
        }
        // 2) Look up the composite id in contest_problems.
        return contestProblemMapper.findByContestIdAndId(contestId, problemPath)
                .map(cp -> extractProblemIdOrThrow(cp, problemPath))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Contest problem not found: " + problemPath));
    }

    /**
     * Extract the numeric problem id from a {@link ContestProblem}, throwing
     * 404 if the row is missing the underlying problem id (data integrity issue).
     */
    private Long extractProblemIdOrThrow(ContestProblem cp, String contestProblemId) {
        if (cp.getProblemId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "Contest problem has no underlying problem id: " + contestProblemId);
        }
        return cp.getProblemId();
    }

    // =========================================================================
    // PARTICIPATION (Authenticated)
    // =========================================================================

    /**
     * Register for a contest.
     * Requires authentication.
     *
     * @param id the contest ID
     * @return success result
     */
    @Operation(summary = "Register for contest", description = "Register the current user for a contest")
    @ApiResponse(responseCode = "200", description = "Registration successful")
    @ApiResponse(responseCode = "400", description = "Already registered or contest not open")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:register", limit = 20, period = 60)
    @PostMapping("/{id}/register")
    public Result<Void> registerForContest(
            @Parameter(description = "Contest ID")
            @PathVariable String id) {

        String resolvedId = resolveContestId(id);
        String userId = getCurrentUserIdOrThrow();
        contestService.registerForContest(resolvedId, userId);
        return Result.success();
    }

    /**
     * Check in to a contest. Currently an alias for register — added 2026-06-11
     * because the frontend's {@code checkIn()} function expected this route.
     * Will split into a separate time-window operation if/when contest check-in
     * windows become a real feature.
     */
    @Operation(summary = "Check in to contest (alias for register)",
            description = "Currently delegates to register. Same business rules apply.")
    @ApiResponse(responseCode = "200", description = "Check-in successful")
    @ApiResponse(responseCode = "400", description = "Already registered or contest not open")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:check-in", limit = 20, period = 60)
    @PostMapping("/{id}/check-in")
    public Result<Void> checkIn(
            @Parameter(description = "Contest ID or slug")
            @PathVariable String id) {

        String resolvedId = resolveContestId(id);
        String userId = getCurrentUserIdOrThrow();
        contestService.registerForContest(resolvedId, userId);
        return Result.success();
    }

    /**
     * Unregister from a contest.
     * Requires authentication.
     *
     * @param id the contest ID
     * @return success result
     */
    @Operation(summary = "Unregister from contest", description = "Unregister the current user from a contest")
    @ApiResponse(responseCode = "200", description = "Unregistration successful")
    @ApiResponse(responseCode = "400", description = "Not registered for this contest")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:unregister", limit = 20, period = 60)
    @DeleteMapping("/{id}/register")
    public Result<Void> unregisterFromContest(
            @Parameter(description = "Contest ID")
            @PathVariable String id) {

        String resolvedId = resolveContestId(id);
        String userId = getCurrentUserIdOrThrow();
        contestService.unregisterFromContest(resolvedId, userId);
        return Result.success();
    }

    /**
     * Get participation status for a contest.
     * Requires authentication.
     *
     * @param id the contest ID
     * @return the participation status
     */
    @Operation(summary = "Get participation status", description = "Get the current user's participation status for a contest")
    @ApiResponse(responseCode = "200", description = "Participation status retrieved", content = @Content(schema = @Schema(implementation = ParticipationStatusDTO.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/{id}/participation")
    public Result<ParticipationStatusDTO> getParticipationStatus(
            @Parameter(description = "Contest ID")
            @PathVariable String id) {

        String resolvedId = resolveContestId(id);
        String userId = getCurrentUserIdOrThrow();
        ParticipationStatusDTO status = contestService.getParticipationStatus(resolvedId, userId);
        return Result.success(status);
    }

    // =========================================================================
    // VIRTUAL CONTEST (Authenticated)
    // =========================================================================

    /**
     * Start a virtual contest.
     * <p>M3: behavior is idempotent — if the user already has an ACTIVE virtual
     * session (is_virtual=1, status=STARTED) for this contest, the existing
     * session is returned. Prior FINISHED virtual sessions are NOT reused; a
     * new replay session is created. Concurrent calls are serialized via
     * row-level lock (SELECT ... FOR UPDATE) so only one session can be
     * created per (contest, user) pair.
     * <p>Requires authentication.
     *
     * @param id the contest ID
     * @return the virtual session information
     */
    @Operation(summary = "Start virtual contest", description = "Start a virtual participation for a past contest. Idempotent on ACTIVE sessions; concurrent calls are serialized.")
    @ApiResponse(responseCode = "200", description = "Virtual contest started", content = @Content(schema = @Schema(implementation = ParticipationStatusDTO.class)))
    @ApiResponse(responseCode = "400", description = "Cannot start virtual contest for non-past contest")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:virtual-start:{id}", limit = 20, period = 60)
    @PostMapping("/{id}/virtual/start")
    public Result<ParticipationStatusDTO> startVirtualContest(
            @Parameter(description = "Contest ID")
            @PathVariable String id) {

        String resolvedId = resolveContestId(id);
        String userId = getCurrentUserIdOrThrow();
        ParticipationStatusDTO status = contestService.startVirtualContest(resolvedId, userId);
        return Result.success(status);
    }

    /**
     * Get virtual contest session status.
     * Requires authentication.
     *
     * @param id the contest ID
     * @return the virtual session status
     */
    @Operation(summary = "Get virtual session", description = "Get the current virtual contest session status")
    @ApiResponse(responseCode = "200", description = "Virtual session retrieved", content = @Content(schema = @Schema(implementation = ParticipationStatusDTO.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Virtual session not found")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/{id}/virtual/session")
    public Result<ParticipationStatusDTO> getVirtualSession(
            @Parameter(description = "Contest ID")
            @PathVariable String id) {

        String resolvedId = resolveContestId(id);
        String userId = getCurrentUserIdOrThrow();
        ParticipationStatusDTO status = contestService.getVirtualSession(resolvedId, userId);
        return Result.success(status);
    }

    /**
     * Finish a virtual contest.
     * Requires authentication.
     *
     * @param id        the contest ID
     * @param sessionId the virtual session ID — optional since 2026-06-11.
     *                  When omitted, the service falls back to the participant's
     *                  stored virtualSessionId. When supplied, it is validated
     *                  against the stored value to defend against tampering.
     * @return success result
     */
    @Operation(summary = "Finish virtual contest", description = "Finish a virtual contest session")
    @ApiResponse(responseCode = "200", description = "Virtual contest finished")
    @ApiResponse(responseCode = "400", description = "No active virtual session or session id mismatch")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Virtual session not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:virtual-finish:{id}", limit = 20, period = 60)
    @PostMapping("/{id}/virtual/finish")
    public Result<Void> finishVirtualContest(
            @Parameter(description = "Contest ID")
            @PathVariable String id,
            @Parameter(description = "Virtual session ID (optional since 2026-06-11)")
            @RequestParam(required = false) String sessionId) {

        String resolvedId = resolveContestId(id);
        String userId = getCurrentUserIdOrThrow();
        contestService.finishVirtualContest(resolvedId, sessionId, userId);
        return Result.success();
    }

    // =========================================================================
    // USER CONTESTS (Authenticated)
    // =========================================================================

    /**
     * Get current user's contests.
     * Requires authentication.
     *
     * @param type the type of contests (registered, participated, virtual)
     * @return list of contests
     */
    @Operation(summary = "Get my contests", description = "Get the current user's contests")
    @ApiResponse(responseCode = "200", description = "User's contests retrieved", content = @Content(schema = @Schema(implementation = java.util.List.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/user/my-contests")
    public Result<List<ContestVO>> getMyContests(
            @Parameter(description = "Type of contests (registered, participated, virtual)")
            @RequestParam(required = false, defaultValue = "participated") String type) {

        String userId = getCurrentUserIdOrThrow();
        List<ContestVO> contests = contestService.getUserContests(userId, type);
        return Result.success(contests);
    }

    /**
     * Get current user's contest history.
     * Requires authentication.
     *
     * @return list of contest history
     */
    @Operation(summary = "Get contest history", description = "Get the current user's contest participation history")
    @ApiResponse(responseCode = "200", description = "Contest history retrieved", content = @Content(schema = @Schema(implementation = java.util.List.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/user/history")
    public Result<List<UserContestHistoryVO>> getContestHistory() {
        String userId = getCurrentUserIdOrThrow();
        List<UserContestHistoryVO> history = rankingService.getUserContestHistory(userId);
        return Result.success(history);
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Resolve a contest ID or slug to the actual database contest ID.
     *
     * @param idOrSlug the contest ID or slug
     * @return the database contest ID, or the original value if not found
     */
    private String resolveContestId(String idOrSlug) {
        if (idOrSlug == null) {
            return null;
        }
        // P3-4 fix: instead of silently falling back to the raw input (which
        // would mask typos and pass a slug string to downstream services that
        // expect a UUID), throw a 404 when neither id nor slug matches.
        return contestService.findById(idOrSlug)
                .map(Contest::getId)
                .or(() -> contestService.findBySlug(idOrSlug).map(Contest::getId))
                .orElseThrow(() -> new com.ulticode.common.exception.BusinessException(
                        com.ulticode.common.exception.ErrorCode.CONTEST_NOT_FOUND,
                        "Contest not found by id or slug: " + idOrSlug));
    }

    /**
     * Get the current authenticated user's ID or throw an exception.
     *
     * @return the user ID
     * @throws BusinessException if not authenticated
     */
    private String getCurrentUserIdOrThrow() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private void validateSubmissionPayload(CreateSubmissionDTO createDTO) {
        Set<ConstraintViolation<CreateSubmissionDTO>> violations = validator.validate(createDTO);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .findFirst()
                    .orElse("Validation failed");
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }
}
