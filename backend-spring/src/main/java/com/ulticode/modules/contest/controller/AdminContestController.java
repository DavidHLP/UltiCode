package com.ulticode.modules.contest.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.projection.ContestProjection;
import com.ulticode.modules.contest.service.ContestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Contests", description = "Contest management endpoints for admin dashboard")
@RestController
@RequestMapping("/admin/contest")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AdminContestController {

    private final ContestService contestService;
    private final ContestProjection contestProjection;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "List all contests (admin)", description = "Get all contests including drafts and invisible ones")
    @ApiResponse(responseCode = "200", description = "Contests retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public Result<PageResult<ContestListVO>> listContests(
            @Parameter(description = "Page number (1-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Number of items per page") @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by contest type") @RequestParam(required = false) String contestType,
            @Parameter(description = "Search by ID, title, or slug") @RequestParam(required = false) String search,
            @Parameter(description = "Sort by field") @RequestParam(required = false) String sort,
            @Parameter(description = "Sort direction") @RequestParam(required = false) String direction) {

        ContestQueryDTO query = new ContestQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setStatus(status);
        query.setContestType(contestType);
        query.setSearch(search);
        query.setSort(sort);
        query.setDirection(direction);

        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(contestProjection.findAllAdmin(query, userId));
    }

    @Operation(summary = "Get contest details (admin)", description = "Get contest details by ID")
    @ApiResponse(responseCode = "200", description = "Contest retrieved", content = @Content(schema = @Schema(implementation = ContestVO.class)))
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{id}")
    public Result<ContestVO> getContest(
            @Parameter(description = "Contest ID") @PathVariable String id) {

        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(contestProjection.getContestById(id, userId));
    }

    @Operation(summary = "Create contest", description = "Create a new contest")
    @ApiResponse(responseCode = "200", description = "Contest created", content = @Content(schema = @Schema(implementation = ContestVO.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:create", limit = 30, period = 60)
    @PostMapping
    public Result<ContestVO> createContest(@Valid @RequestBody CreateContestDTO dto) {
        rejectUnsafeTitleChars(dto.getTitle());
        String userId = getCurrentUserIdOrThrow();
        return Result.success(contestService.createContest(dto, userId));
    }

    @Operation(summary = "Update contest (partial)", description = "Partially update a contest")
    @ApiResponse(responseCode = "200", description = "Contest updated", content = @Content(schema = @Schema(implementation = ContestVO.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:update", limit = 30, period = 60)
    @PatchMapping("/{id}")
    public Result<ContestVO> updateContest(
            @Parameter(description = "Contest ID") @PathVariable String id,
            @Valid @RequestBody UpdateContestDTO dto) {
        rejectUnsafeTitleChars(dto.getTitle());
        return Result.success(contestService.updateContest(id, dto));
    }

    @Operation(summary = "Delete contest", description = "Soft delete a contest")
    @ApiResponse(responseCode = "200", description = "Contest deleted")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    public Result<Void> deleteContest(
            @Parameter(description = "Contest ID") @PathVariable String id) {

        contestService.deleteContest(id);
        return Result.success();
    }

    @Operation(summary = "Add problem to contest", description = "Add a problem to a contest")
    @ApiResponse(responseCode = "200", description = "Problem added", content = @Content(schema = @Schema(implementation = ContestProblemVO.class)))
    @ApiResponse(responseCode = "400", description = "Problem already in contest")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:add-problem", limit = 30, period = 60)
    @PostMapping("/{id}/problems")
    public Result<ContestProblemVO> addProblem(
            @Parameter(description = "Contest ID") @PathVariable String id,
            @Valid @RequestBody AddContestProblemDTO dto) {

        return Result.success(contestService.addProblem(id, dto));
    }

    @Operation(summary = "Remove problem from contest", description = "Remove a problem from a contest")
    @ApiResponse(responseCode = "200", description = "Problem removed")
    @ApiResponse(responseCode = "400", description = "Problem not in contest")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:remove-problem", limit = 30, period = 60)
    @DeleteMapping("/{id}/problems/{problemId}")
    public Result<Void> removeProblem(
            @Parameter(description = "Contest ID") @PathVariable String id,
            @Parameter(description = "Problem ID") @PathVariable Long problemId) {

        contestService.removeProblem(id, problemId);
        return Result.success();
    }

    @Operation(summary = "Get contest rankings (admin)", description = "Get rankings for a specific contest")
    @ApiResponse(responseCode = "200", description = "Rankings retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{id}/rankings")
    public Result<PageResult<ContestRankingVO>> getRankings(
            @Parameter(description = "Contest ID") @PathVariable String id,
            @Parameter(description = "Page number (1-based)") @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "Number of items per page") @RequestParam(required = false, defaultValue = "50") Integer limit) {

        return Result.success(contestProjection.getAdminContestRanking(id, page, limit));
    }

    @Operation(summary = "Start contest", description = "Transition a contest from DRAFT/UPCOMING to RUNNING")
    @ApiResponse(responseCode = "200", description = "Contest started", content = @Content(schema = @Schema(implementation = ContestVO.class)))
    @ApiResponse(responseCode = "400", description = "Invalid contest status for starting")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:start", limit = 30, period = 60)
    @PostMapping("/{id}/start")
    public Result<ContestVO> startContest(
            @Parameter(description = "Contest ID") @PathVariable String id) {

        String userId = getCurrentUserIdOrThrow();
        return Result.success(contestService.startContest(id, userId));
    }

    @Operation(summary = "End contest", description = "Transition a contest from RUNNING to FINISHED")
    @ApiResponse(responseCode = "200", description = "Contest ended", content = @Content(schema = @Schema(implementation = ContestVO.class)))
    @ApiResponse(responseCode = "400", description = "Contest is not running")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:end", limit = 30, period = 60)
    @PostMapping("/{id}/end")
    public Result<ContestVO> endContest(
            @Parameter(description = "Contest ID") @PathVariable String id) {

        String userId = getCurrentUserIdOrThrow();
        return Result.success(contestService.endContest(id, userId));
    }

    private String getCurrentUserIdOrThrow() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * Defense-in-depth guard against HTML angle-bracket characters in titles.
     * The {@code @Pattern} on {@code CreateContestDTO.title} accepts {@code \\p{P}}
     * (Unicode punctuation), which includes {@code <} and {@code >}. While the
     * rendering layer applies OWASP Encoder, the principle of "reject at the
     * boundary" is enforced here so the stored value cannot contain
     * HTML-significant characters regardless of downstream rendering.
     */
    private void rejectUnsafeTitleChars(String title) {
        if (title != null && (title.contains("<") || title.contains(">"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Title must not contain < or >");
        }
    }
}
