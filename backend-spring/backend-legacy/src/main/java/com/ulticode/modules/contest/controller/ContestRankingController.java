package com.ulticode.modules.contest.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.contest.controller.internal.ContestControllerSupport;
import com.ulticode.modules.contest.dto.ContestRankingVO;
import com.ulticode.modules.contest.dto.LiveRankingEntryVO;
import com.ulticode.modules.contest.port.ContestLiveRankingReadPort;
import com.ulticode.modules.contest.projection.ContestProjection;
import com.ulticode.modules.contest.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Contest ranking endpoints — read view, public. No auth, no state mutation.
 *
 * <p>Global ranking reads depend on {@link ContestProjection}; paginated
 * per-contest ranking depends on {@link RankingService}; live ranking reads
 * depend on {@link ContestLiveRankingReadPort}. Path resolution goes through
 * the projection (id-or-slug → id).
 */
@Tag(name = "Contest Ranking", description = "Contest leaderboard endpoints")
@RestController
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestRankingController {

    private final ContestProjection contestProjection;
    private final RankingService rankingService;
    private final ContestLiveRankingReadPort liveRankingReadPort;

    @Operation(summary = "Get global ranking",
            description = "Get the global leaderboard")
    @GetMapping("/global-ranking")
    public Result<List<ContestRankingVO>> getGlobalRanking(
            @Parameter(description = "Maximum number of rankings to return")
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return Result.success(contestProjection.getGlobalRanking(limit));
    }

    @Operation(summary = "Get global rankings with pagination",
            description = "Get paginated global leaderboard")
    @GetMapping("/rankings/global")
    public Result<PageResult<ContestRankingVO>> getGlobalRankingsPaginated(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @Parameter(description = "Country filter (matches global_rankings.country)")
            @RequestParam(required = false) String country) {
        return Result.success(contestProjection.getGlobalRankingsPaginated(page, limit, country));
    }

    @Operation(summary = "Get contest ranking",
            description = "Get the ranking for a specific contest")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}/ranking")
    public Result<PageResult<ContestRankingVO>> getContestRanking(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        return Result.success(rankingService.getContestRanking(resolvedId, page, limit));
    }

    @Operation(summary = "Get live ranking",
            description = "Get the live ranking for a running contest")
    @ApiResponse(responseCode = "403", description = "Contest is not currently running")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @GetMapping("/{id}/live-ranking")
    public Result<List<LiveRankingEntryVO>> getLiveRanking(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        int effectiveLimit = (limit != null) ? limit : 100;
        return Result.success(liveRankingReadPort.readLiveRanking(resolvedId, effectiveLimit));
    }
}
