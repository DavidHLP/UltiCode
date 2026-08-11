package com.ulticode.modules.admin.controller;

import com.ulticode.app.api.dto.ContestProblemAdminDTO;
import com.ulticode.app.api.dto.ContestRankingEntryDTO;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.admin.dto.AddContestProblemDTO;
import com.ulticode.modules.admin.dto.AdminContestQueryDTO;
import com.ulticode.modules.admin.dto.AdminContestVO;
import com.ulticode.modules.admin.dto.CreateContestDTO;
import com.ulticode.modules.admin.dto.UpdateContestDTO;
import com.ulticode.modules.admin.service.AdminContestService;
import com.ulticode.modules.admin.service.ContestCutoverService;
import com.ulticode.websecurity.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/contest")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
@Tag(name = "Admin - Contests", description = "Contest management endpoints for admin dashboard")
public class AdminContestController {

    private final AdminContestService adminContestService;
    private final ContestCutoverService contestCutoverService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "List all contests (admin)")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping
    public Result<PageResult<AdminContestVO>> listContests(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contestType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        AdminContestQueryDTO query = new AdminContestQueryDTO();
        query.setPage(page);
        query.setLimit(pageSize);
        query.setStatus(status);
        query.setType(contestType);
        query.setSearch(search);
        query.setSortBy(sort);
        query.setSortOrder(direction);
        return Result.success(adminContestService.getContests(query));
    }

    @Operation(summary = "Get contest details (admin)")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{id}")
    public Result<AdminContestVO> getContest(@PathVariable String id) {
        return Result.success(adminContestService.getContest(id));
    }

    @Operation(summary = "Create contest")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:create", limit = 30, period = 60)
    @PostMapping
    public Result<AdminContestVO> createContest(
            @Valid @RequestBody CreateContestDTO dto,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        rejectUnsafeTitleChars(dto.getTitle());
        return Result.success(contestCutoverService.createContest(
                dto, getCurrentUserIdOrThrow(), idempotencyKey));
    }

    @Operation(summary = "Update contest")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:update", limit = 30, period = 60)
    @PatchMapping("/{id}")
    public Result<AdminContestVO> updateContest(
            @PathVariable String id,
            @Valid @RequestBody UpdateContestDTO dto,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        rejectUnsafeTitleChars(dto.getTitle());
        return Result.success(contestCutoverService.updateContest(id, dto, idempotencyKey));
    }

    @Operation(summary = "Delete contest")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    public Result<Void> deleteContest(
            @PathVariable String id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        contestCutoverService.deleteContest(id, idempotencyKey);
        return Result.success();
    }

    @Operation(summary = "Add problem to contest")
    @ApiResponse(responseCode = "200", description = "Problem added",
            content = @Content(schema = @Schema(implementation = ContestProblemAdminDTO.class)))
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:add-problem", limit = 30, period = 60)
    @PostMapping("/{id}/problems")
    public Result<ContestProblemAdminDTO> addProblem(
            @PathVariable String id,
            @Valid @RequestBody AddContestProblemDTO dto,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(contestCutoverService.addProblem(id, dto, idempotencyKey));
    }

    @Operation(summary = "Remove problem from contest")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:remove-problem", limit = 30, period = 60)
    @DeleteMapping("/{id}/problems/{problemId}")
    public Result<Void> removeProblem(
            @PathVariable String id,
            @PathVariable Long problemId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        contestCutoverService.removeProblem(id, problemId, idempotencyKey);
        return Result.success();
    }

    @Operation(summary = "Get contest rankings (admin)")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/{id}/rankings")
    public Result<PageResult<ContestRankingEntryDTO>> getRankings(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        return Result.success(adminContestService.getRankings(id, page, limit));
    }

    @Operation(summary = "Start contest")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:start", limit = 30, period = 60)
    @PostMapping("/{id}/start")
    public Result<AdminContestVO> startContest(
            @PathVariable String id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(contestCutoverService.startContest(id, idempotencyKey));
    }

    @Operation(summary = "End contest")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "admin-contest:end", limit = 30, period = 60)
    @PostMapping("/{id}/end")
    public Result<AdminContestVO> endContest(
            @PathVariable String id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(contestCutoverService.endContest(id, idempotencyKey));
    }

    private String getCurrentUserIdOrThrow() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private static void rejectUnsafeTitleChars(String title) {
        if (title != null && (title.contains("<") || title.contains(">"))) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Title must not contain < or >");
        }
    }
}
