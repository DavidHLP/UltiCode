package com.ulticode.modules.problemlist.controller;

import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.problemlist.dto.*;
import com.ulticode.modules.problemlist.projection.ProblemListProjection;
import com.ulticode.modules.problemlist.service.ProblemListService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for problem list operations.
 */
@Tag(name = "ProblemList", description = "Problem list management API")
@RestController
@RequestMapping("/problem-lists")
@RequiredArgsConstructor
public class ProblemListController {

    private final ProblemListService problemListService;
    private final ProblemListProjection problemListProjection;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Get problem lists overview")
    @GetMapping("/overview")
    public Result<UserProblemListsVO> getOverview(
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId != null) {
            return Result.success(problemListProjection.getUserProblemLists(userId));
        }
        return Result.success(problemListProjection.findAll(locale));
    }

    @Operation(summary = "Get problem list detail")
    @GetMapping("/{id}/overview")
    public Result<ProblemListDetailVO> getListOverview(
            @PathVariable String id,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(problemListProjection.getListOverview(id, userId, locale));
    }

    @Operation(summary = "Create a problem list")
    @RateLimit(key = "problem-list:create", limit = 20, period = 60)
    @PostMapping
    @SecurityRequirement(name = "Bearer")
    public Result<ProblemListSummaryVO> createList(
            @Valid @RequestBody CreateProblemListDTO dto) {
        String userId = requireAuthenticatedUserId();
        return Result.success(problemListService.createList(userId, dto));
    }

    @Operation(summary = "Update a problem list")
    @RateLimit(key = "problem-list:update", limit = 20, period = 60)
    @PatchMapping("/{id}")
    @SecurityRequirement(name = "Bearer")
    public Result<ProblemListSummaryVO> updateList(
            @PathVariable String id,
            @Valid @RequestBody UpdateProblemListDTO dto) {
        String userId = requireAuthenticatedUserId();
        return Result.success(problemListService.updateList(id, userId, dto));
    }

    @Operation(summary = "Delete a problem list")
    @RateLimit(key = "problem-list:delete", limit = 20, period = 60)
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> deleteList(@PathVariable String id) {
        String userId = requireAuthenticatedUserId();
        problemListService.deleteList(id, userId);
        return Result.success();
    }

    @Operation(summary = "Fork a problem list")
    @RateLimit(key = "problem-list:fork", limit = 20, period = 60)
    @PostMapping("/{id}/fork")
    @SecurityRequirement(name = "Bearer")
    public Result<ProblemListSummaryVO> forkList(@PathVariable String id) {
        String userId = requireAuthenticatedUserId();
        return Result.success(problemListService.forkList(id, userId));
    }

    @Operation(summary = "Add a problem to a list")
    @RateLimit(key = "problem-list:add-problem", limit = 20, period = 60)
    @PostMapping("/{id}/problems")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> addProblem(
            @PathVariable String id,
            @Valid @RequestBody AddProblemToListDTO dto) {
        String userId = requireAuthenticatedUserId();
        problemListService.addProblem(id, userId, dto.getProblemId());
        return Result.success();
    }

    @Operation(summary = "Remove a problem from a list")
    @RateLimit(key = "problem-list:remove-problem", limit = 20, period = 60)
    @DeleteMapping("/{id}/problems/{problemId}")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> removeProblem(
            @PathVariable String id,
            @PathVariable Long problemId) {
        String userId = requireAuthenticatedUserId();
        problemListService.removeProblem(id, userId, problemId);
        return Result.success();
    }

    @Operation(summary = "Save a problem list")
    @RateLimit(key = "problem-list:save", limit = 20, period = 60)
    @PostMapping("/{id}/save")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> saveList(
            @PathVariable String id,
            @RequestBody(required = false) SaveListDTO dto) {
        String userId = requireAuthenticatedUserId();
        problemListService.saveList(userId, id, dto != null ? dto.getCategoryId() : null);
        return Result.success();
    }

    @Operation(summary = "Unsave a problem list")
    @RateLimit(key = "problem-list:unsave", limit = 20, period = 60)
    @DeleteMapping("/{id}/save")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> unsaveList(@PathVariable String id) {
        String userId = requireAuthenticatedUserId();
        problemListService.unsaveList(userId, id);
        return Result.success();
    }

    @Operation(summary = "Get user's lists for a problem")
    @GetMapping("/problems/{problemId}/user-lists")
    @SecurityRequirement(name = "Bearer")
    public Result<UserListsForProblemVO> getUserListsForProblem(
            @PathVariable Long problemId) {
        String userId = requireAuthenticatedUserId();
        return Result.success(problemListProjection.getUserListsForProblem(userId, problemId));
    }

    @Operation(summary = "Batch add a problem to multiple lists")
    @RateLimit(key = "problem-list:batch-add", limit = 20, period = 60)
    @PostMapping("/problems/{problemId}/batch-add")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> batchAddProblemToLists(
            @PathVariable Long problemId,
            @Valid @RequestBody BatchAddToListsDTO dto) {
        String userId = requireAuthenticatedUserId();
        problemListService.batchAddProblemToLists(userId, problemId, dto.getListIds());
        return Result.success();
    }

    @Operation(summary = "Batch remove a problem from multiple lists")
    @RateLimit(key = "problem-list:batch-remove", limit = 20, period = 60)
    @PostMapping("/problems/{problemId}/batch-remove")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> batchRemoveProblemFromLists(
            @PathVariable Long problemId,
            @Valid @RequestBody BatchAddToListsDTO dto) {
        String userId = requireAuthenticatedUserId();
        problemListService.batchRemoveProblemFromLists(userId, problemId, dto.getListIds());
        return Result.success();
    }

    @Operation(summary = "Move a list to a category")
    @RateLimit(key = "problem-list:move-category", limit = 20, period = 60)
    @PatchMapping("/{id}/category")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> moveListToCategory(
            @PathVariable String id,
            @Valid @RequestBody MoveListToCategoryDTO dto) {
        String userId = requireAuthenticatedUserId();
        problemListService.moveListToCategory(userId, id, dto.getCategoryId());
        return Result.success();
    }

    // ==================== Category Management ====================

    @Operation(summary = "Create a category")
    @RateLimit(key = "problem-list:create-category", limit = 20, period = 60)
    @PostMapping("/categories")
    @SecurityRequirement(name = "Bearer")
    public Result<CategorySummaryVO> createCategory(
            @Valid @RequestBody CreateCategoryDTO dto) {
        String userId = requireAuthenticatedUserId();
        return Result.success(problemListService.createCategory(userId, dto));
    }

    @Operation(summary = "Update a category")
    @RateLimit(key = "problem-list:update-category", limit = 20, period = 60)
    @PatchMapping("/categories/{categoryId}")
    @SecurityRequirement(name = "Bearer")
    public Result<CategorySummaryVO> updateCategory(
            @PathVariable String categoryId,
            @Valid @RequestBody UpdateCategoryDTO dto) {
        String userId = requireAuthenticatedUserId();
        return Result.success(problemListService.updateCategory(categoryId, userId, dto));
    }

    @Operation(summary = "Delete a category")
    @RateLimit(key = "problem-list:delete-category", limit = 20, period = 60)
    @DeleteMapping("/categories/{categoryId}")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> deleteCategory(@PathVariable String categoryId) {
        String userId = requireAuthenticatedUserId();
        problemListService.deleteCategory(categoryId, userId);
        return Result.success();
    }

    private String requireAuthenticatedUserId() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
