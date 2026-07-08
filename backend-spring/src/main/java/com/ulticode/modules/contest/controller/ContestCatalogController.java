package com.ulticode.modules.contest.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.contest.controller.internal.ContestControllerSupport;
import com.ulticode.modules.contest.dto.ContestListVO;
import com.ulticode.modules.contest.dto.ContestProblemVO;
import com.ulticode.modules.contest.dto.ContestQueryDTO;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.GlobalContestStatsVO;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.projection.ContestProjection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only contest catalog: list, upcoming, running, past, stats,
 * by-id, problems, announcements. The deep module is intentionally
 * public — it does not require auth, but uses the optional
 * current-user-id to enrich responses.
 *
 * <p>All endpoints are pure reads and depend on {@link ContestProjection}
 * directly.
 */
@Tag(name = "Contest Catalog", description = "Public contest catalog endpoints")
@RestController
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestCatalogController {

    private final ContestProjection contestProjection;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Get contest list",
            description = "Get a paginated list of contests with optional filters")
    @GetMapping
    public Result<PageResult<ContestListVO>> getContestList(
            @Parameter(description = "Page number (1-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Number of items per page") @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Search by ID, title, or slug") @RequestParam(required = false) String search,
            @Parameter(description = "Sort field") @RequestParam(required = false) String sort,
            @Parameter(description = "Sort direction") @RequestParam(required = false) String direction,
            @Parameter(description = "Filter by contest type") @RequestParam(required = false) String contestType,
            @Parameter(description = "Filter by rated status") @RequestParam(required = false) Boolean isRated) {

        ContestQueryDTO query = new ContestQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setStatus(status);
        query.setSearch(search);
        query.setSort(sort);
        query.setDirection(direction);
        query.setContestType(contestType);
        query.setIsRated(isRated);

        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(contestProjection.findAllListVO(query, userId));
    }

    @Operation(summary = "Get upcoming contests",
            description = "Get a paginated list of upcoming contests")
    @GetMapping("/upcoming")
    public Result<PageResult<ContestListVO>> getUpcomingContests() {
        return Result.success(contestProjection.findUpcoming(currentUserProvider.getCurrentUserId()));
    }

    @Operation(summary = "Get running contests",
            description = "Get a paginated list of currently running contests")
    @GetMapping("/running")
    public Result<PageResult<ContestListVO>> getRunningContests() {
        return Result.success(contestProjection.findRunning(currentUserProvider.getCurrentUserId()));
    }

    @Operation(summary = "Get past contests",
            description = "Get a paginated list of past contests")
    @GetMapping("/past")
    public Result<PageResult<ContestListVO>> getPastContests(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(contestProjection.findPast(page, pageSize, currentUserProvider.getCurrentUserId()));
    }

    @Operation(summary = "Get contest statistics",
            description = "Get overall contest statistics")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = GlobalContestStatsVO.class)))
    @GetMapping("/stats")
    public Result<GlobalContestStatsVO> getContestStats() {
        return Result.success(contestProjection.getStats());
    }

    @Operation(summary = "Get contest by ID",
            description = "Get a contest's details by its ID")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ContestVO.class)))
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}")
    public Result<ContestVO> getContestById(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        return Result.success(contestProjection.getContestById(resolvedId, currentUserProvider.getCurrentUserId()));
    }

    @Operation(summary = "Get contest problems",
            description = "Get the problems for a specific contest")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}/problems")
    public Result<List<ContestProblemVO>> getContestProblems(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        return Result.success(contestProjection.getContestProblems(resolvedId));
    }

    @Operation(summary = "Get contest announcements",
            description = "Get the announcements for a specific contest")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}/announcements")
    public Result<List<ContestAnnouncement>> getContestAnnouncements(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        return Result.success(contestProjection.getContestAnnouncements(resolvedId));
    }
}
