package com.ulticode.modules.contest.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.contest.controller.internal.ContestControllerSupport;
import com.ulticode.modules.contest.dto.ContestListVO;
import com.ulticode.modules.contest.dto.ContestProblemVO;
import com.ulticode.modules.contest.dto.ContestQueryDTO;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.GlobalContestStatsVO;
import com.ulticode.modules.contest.entity.ContestAnnouncement;
import com.ulticode.modules.contest.service.ContestService;
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
 */
@Tag(name = "Contest Catalog", description = "Public contest catalog endpoints")
@RestController
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestCatalogController {

    private final ContestService contestService;

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

        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(contestService.findAllListVO(query, userId));
    }

    @Operation(summary = "Get upcoming contests",
            description = "Get a paginated list of upcoming contests")
    @GetMapping("/upcoming")
    public Result<PageResult<ContestListVO>> getUpcomingContests() {
        return Result.success(contestService.findUpcoming(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "Get running contests",
            description = "Get a paginated list of currently running contests")
    @GetMapping("/running")
    public Result<PageResult<ContestListVO>> getRunningContests() {
        return Result.success(contestService.findRunning(SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "Get past contests",
            description = "Get a paginated list of past contests")
    @GetMapping("/past")
    public Result<PageResult<ContestListVO>> getPastContests(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(contestService.findPast(page, pageSize, SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "Get contest statistics",
            description = "Get overall contest statistics")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = GlobalContestStatsVO.class)))
    @GetMapping("/stats")
    public Result<GlobalContestStatsVO> getContestStats() {
        return Result.success(contestService.getStats());
    }

    @Operation(summary = "Get contest by ID",
            description = "Get a contest's details by its ID")
    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ContestVO.class)))
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}")
    public Result<ContestVO> getContestById(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestService, id);
        return Result.success(contestService.getContestById(resolvedId, SecurityUtil.getCurrentUserId()));
    }

    @Operation(summary = "Get contest problems",
            description = "Get the problems for a specific contest")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}/problems")
    public Result<List<ContestProblemVO>> getContestProblems(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestService, id);
        return Result.success(contestService.getContestProblems(resolvedId));
    }

    @Operation(summary = "Get contest announcements",
            description = "Get the announcements for a specific contest")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}/announcements")
    public Result<List<ContestAnnouncement>> getContestAnnouncements(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestService, id);
        return Result.success(contestService.getContestAnnouncements(resolvedId));
    }
}
