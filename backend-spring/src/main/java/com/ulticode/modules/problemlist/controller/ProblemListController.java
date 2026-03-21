package com.ulticode.modules.problemlist.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.problemlist.dto.*;
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
@RequestMapping("/api/problem-lists")
@RequiredArgsConstructor
public class ProblemListController {

    private final ProblemListService problemListService;

    @Operation(summary = "Get problem lists overview")
    @GetMapping("/overview")
    public Result<UserProblemListsVO> getOverview(
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        if (userId != null) {
            return Result.success(problemListService.getUserProblemLists(userId));
        }
        return Result.success(problemListService.findAll(locale));
    }

    @Operation(summary = "Get problem list detail")
    @GetMapping("/{id}/overview")
    public Result<ProblemListDetailVO> getListOverview(
            @PathVariable String id,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = "Accept-Language", required = false) String locale) {
        return Result.success(problemListService.getListOverview(id, userId, locale));
    }

    @Operation(summary = "Create a problem list")
    @PostMapping
    @SecurityRequirement(name = "Bearer")
    public Result<ProblemListSummaryVO> createList(
            @RequestParam String userId,
            @Valid @RequestBody CreateProblemListDTO dto) {
        return Result.success(problemListService.createList(userId, dto));
    }

    @Operation(summary = "Update a problem list")
    @PatchMapping("/{id}")
    @SecurityRequirement(name = "Bearer")
    public Result<ProblemListSummaryVO> updateList(
            @PathVariable String id,
            @RequestParam String userId,
            @Valid @RequestBody UpdateProblemListDTO dto) {
        return Result.success(problemListService.updateList(id, userId, dto));
    }

    @Operation(summary = "Delete a problem list")
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> deleteList(
            @PathVariable String id,
            @RequestParam String userId) {
        problemListService.deleteList(id, userId);
        return Result.success();
    }

    @Operation(summary = "Fork a problem list")
    @PostMapping("/{id}/fork")
    @SecurityRequirement(name = "Bearer")
    public Result<ForkResultVO> forkList(
            @PathVariable String id,
            @RequestParam String userId) {
        String newListId = problemListService.forkList(id, userId);
        return Result.success(new ForkResultVO(newListId));
    }

    @Operation(summary = "Add a problem to a list")
    @PostMapping("/{id}/problems")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> addProblem(
            @PathVariable String id,
            @RequestParam String userId,
            @Valid @RequestBody AddProblemToListDTO dto) {
        problemListService.addProblem(id, userId, dto.getProblemId());
        return Result.success();
    }

    @Operation(summary = "Remove a problem from a list")
    @DeleteMapping("/{id}/problems/{problemId}")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> removeProblem(
            @PathVariable String id,
            @PathVariable Long problemId,
            @RequestParam String userId) {
        problemListService.removeProblem(id, userId, problemId);
        return Result.success();
    }

    @Operation(summary = "Save a problem list")
    @PostMapping("/{id}/save")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> saveList(
            @PathVariable String id,
            @RequestParam String userId,
            @RequestBody(required = false) SaveListDTO dto) {
        problemListService.saveList(userId, id, dto != null ? dto.getCategoryId() : null);
        return Result.success();
    }

    @Operation(summary = "Unsave a problem list")
    @DeleteMapping("/{id}/save")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> unsaveList(
            @PathVariable String id,
            @RequestParam String userId) {
        problemListService.unsaveList(userId, id);
        return Result.success();
    }

    @Operation(summary = "Get user's lists for a problem")
    @GetMapping("/problems/{problemId}/user-lists")
    @SecurityRequirement(name = "Bearer")
    public Result<UserListsForProblemVO> getUserListsForProblem(
            @PathVariable Long problemId,
            @RequestParam String userId) {
        return Result.success(problemListService.getUserListsForProblem(userId, problemId));
    }

    @Operation(summary = "Batch add a problem to multiple lists")
    @PostMapping("/problems/{problemId}/batch-add")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> batchAddProblemToLists(
            @PathVariable Long problemId,
            @RequestParam String userId,
            @Valid @RequestBody BatchAddToListsDTO dto) {
        problemListService.batchAddProblemToLists(userId, problemId, dto.getListIds());
        return Result.success();
    }

    @Operation(summary = "Batch remove a problem from multiple lists")
    @PostMapping("/problems/{problemId}/batch-remove")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> batchRemoveProblemFromLists(
            @PathVariable Long problemId,
            @RequestParam String userId,
            @Valid @RequestBody BatchAddToListsDTO dto) {
        problemListService.batchRemoveProblemFromLists(userId, problemId, dto.getListIds());
        return Result.success();
    }

    @Operation(summary = "Move a list to a category")
    @PatchMapping("/{id}/category")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> moveListToCategory(
            @PathVariable String id,
            @RequestParam String userId,
            @Valid @RequestBody MoveListToCategoryDTO dto) {
        problemListService.moveListToCategory(userId, id, dto.getCategoryId());
        return Result.success();
    }

    // ==================== Category Management ====================

    @Operation(summary = "Create a category")
    @PostMapping("/categories")
    @SecurityRequirement(name = "Bearer")
    public Result<CategorySummaryVO> createCategory(
            @RequestParam String userId,
            @Valid @RequestBody CreateCategoryDTO dto) {
        return Result.success(problemListService.createCategory(userId, dto));
    }

    @Operation(summary = "Update a category")
    @PatchMapping("/categories/{categoryId}")
    @SecurityRequirement(name = "Bearer")
    public Result<CategorySummaryVO> updateCategory(
            @PathVariable String categoryId,
            @RequestParam String userId,
            @Valid @RequestBody UpdateCategoryDTO dto) {
        return Result.success(problemListService.updateCategory(categoryId, userId, dto));
    }

    @Operation(summary = "Delete a category")
    @DeleteMapping("/categories/{categoryId}")
    @SecurityRequirement(name = "Bearer")
    public Result<Void> deleteCategory(
            @PathVariable String categoryId,
            @RequestParam String userId) {
        problemListService.deleteCategory(categoryId, userId);
        return Result.success();
    }
}
