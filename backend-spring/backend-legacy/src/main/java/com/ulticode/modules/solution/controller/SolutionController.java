package com.ulticode.modules.solution.controller;

import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.solution.dto.CreateSolutionCommentDTO;
import com.ulticode.modules.solution.dto.CreateSolutionDTO;
import com.ulticode.modules.solution.dto.RecordViewRequest;
import com.ulticode.modules.solution.dto.SolutionCommentVO;
import com.ulticode.modules.solution.dto.SolutionListItemVO;
import com.ulticode.modules.solution.dto.SolutionVO;
import com.ulticode.modules.solution.dto.UpdateSolutionCommentDTO;
import com.ulticode.modules.solution.dto.UpdateSolutionDTO;
import com.ulticode.modules.solution.projection.SolutionProjection;
import com.ulticode.modules.solution.service.SolutionService;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for solution-related operations.
 */
@Tag(name = "Solution", description = "Solution management endpoints")
@RestController
@RequiredArgsConstructor
public class SolutionController {

    private final SolutionService solutionService;
    private final SolutionProjection solutionProjection;
    private final CurrentUserProvider currentUserProvider;

    /**
     * List solutions for a specific problem.
     * Public endpoint - accessible without authentication.
     *
     * @param problemId the problem ID
     * @param page      the page number (1-based)
     * @param pageSize  the number of items per page
     * @return paginated list of solutions
     */
    @Operation(summary = "List solutions for problem", description = "Get a paginated list of solutions for a specific problem")
    @GetMapping("/api/problems/{problemId}/solutions")
    public Result<PageResult<SolutionListItemVO>> findByProblemId(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false) Integer pageSize) {

        PageResult<SolutionListItemVO> result = solutionProjection.findByProblemId(problemId, page, pageSize);
        return Result.success(result);
    }

    /**
     * Create a new solution for a problem.
     * Requires authentication.
     *
     * @param problemId the problem ID
     * @param createDTO the create data
     * @return the created solution
     */
    @Operation(summary = "Create solution", description = "Create a new solution for a problem")
    @RateLimit(key = "solution:create", limit = 20, period = 60)
    @PostMapping("/api/problems/{problemId}/solutions")
    public Result<SolutionVO> create(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId,
            @Valid @RequestBody CreateSolutionDTO createDTO) {

        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SolutionVO solution = solutionService.create(problemId, userId, createDTO);
        return Result.success(solution);
    }

    /**
     * Get a solution by ID.
     * Public endpoint - accessible without authentication.
     *
     * @param id the solution ID
     * @return the solution details
     */
    @Operation(summary = "Get solution by ID", description = "Get a solution's details by its ID")
    @GetMapping("/api/solutions/{id}")
    public Result<SolutionVO> getSolutionById(
            @Parameter(description = "Solution ID")
            @PathVariable String id,
            @Parameter(description = "Current user ID (optional)")
            @RequestParam(required = false) String userId) {

        SolutionVO solution = solutionService.getSolutionById(id, userId);
        return Result.success(solution);
    }

    /**
     * Update an existing solution.
     * Requires authentication. Only the author can update their own solutions.
     *
     * @param id        the solution ID
     * @param updateDTO the update data
     * @return the updated solution
     */
    @Operation(summary = "Update solution", description = "Update an existing solution (author only)")
    @RateLimit(key = "solution:update", limit = 20, period = 60)
    @PutMapping("/api/solutions/{id}")
    public Result<SolutionVO> update(
            @Parameter(description = "Solution ID")
            @PathVariable String id,
            @Valid @RequestBody UpdateSolutionDTO updateDTO) {

        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SolutionVO solution = solutionService.update(id, userId, updateDTO);
        return Result.success(solution);
    }

    /**
     * Delete a solution.
     * Requires authentication. Only the author can delete their own solutions.
     *
     * @param id the solution ID
     * @return success result
     */
    @Operation(summary = "Delete solution", description = "Delete a solution (author only)")
    @RateLimit(key = "solution:delete", limit = 20, period = 60)
    @DeleteMapping("/api/solutions/{id}")
    public Result<Void> delete(
            @Parameter(description = "Solution ID")
            @PathVariable String id) {

        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        solutionService.delete(id, userId);
        return Result.success();
    }

    /**
     * Get solutions by user ID.
     * Public endpoint - accessible without authentication.
     *
     * @param userId the user ID
     * @param problemId optional problem ID to filter by
     * @return list of solutions by the user
     */
    @Operation(summary = "Get solutions by user ID", description = "Get all solutions published by a specific user")
    @GetMapping("/api/solutions")
    public Result<List<SolutionVO>> findByUserId(
            @Parameter(description = "User ID")
            @RequestParam String userId,
            @Parameter(description = "Problem ID (optional)")
            @RequestParam(required = false) Long problemId) {

        List<SolutionVO> solutions = solutionProjection.findByUserId(userId, problemId);
        return Result.success(solutions);
    }

    /**
     * Record a view for a solution.
     * Public endpoint - accessible without authentication.
     *
     * @param solutionId the solution ID
     * @param request the view request containing user ID
     * @return success result
     */
    @Operation(summary = "Record solution view", description = "Record a view for a solution")
    @RateLimit(key = "solution:view", limit = 20, period = 60)
    @PostMapping("/api/views/solution/{solutionId}")
    public Result<Void> recordView(
            @Parameter(description = "Solution ID")
            @PathVariable String solutionId,
            @RequestBody RecordViewRequest request) {

        solutionService.recordView(solutionId, request != null ? request.getUserId() : null);
        return Result.success();
    }

    /**
     * Get comments for a solution.
     * Public endpoint - accessible without authentication.
     *
     * @param solutionId the solution ID
     * @return list of comments
     */
    @Operation(summary = "Get solution comments", description = "Get all comments for a solution")
    @GetMapping("/api/solutions/{solutionId}/comments")
    public Result<List<SolutionCommentVO>> getComments(
            @Parameter(description = "Solution ID")
            @PathVariable String solutionId) {

        List<SolutionCommentVO> comments = solutionProjection.getComments(solutionId);
        return Result.success(comments);
    }

    @Operation(summary = "Create solution comment", description = "Add a comment to a solution")
    @PostMapping("/api/solutions/{solutionId}/comments")
    public Result<SolutionCommentVO> createComment(
            @Parameter(description = "Solution ID")
            @PathVariable String solutionId,
            @Valid @RequestBody CreateSolutionCommentDTO dto) {

        if (!currentUserProvider.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String userId = currentUserProvider.getCurrentUserId();
        SolutionCommentVO comment = solutionService.createComment(solutionId, userId, dto);
        return Result.success(comment);
    }

    @Operation(summary = "Update solution comment", description = "Edit an existing comment")
    @PatchMapping("/api/solutions/comments/{commentId}")
    public Result<SolutionCommentVO> updateComment(
            @Parameter(description = "Comment ID")
            @PathVariable String commentId,
            @Valid @RequestBody UpdateSolutionCommentDTO dto) {

        if (!currentUserProvider.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String userId = currentUserProvider.getCurrentUserId();
        SolutionCommentVO comment = solutionService.updateComment(commentId, userId, dto);
        return Result.success(comment);
    }

    @Operation(summary = "Delete solution comment", description = "Soft-delete a comment")
    @DeleteMapping("/api/solutions/comments/{commentId}")
    public Result<Void> deleteComment(
            @Parameter(description = "Comment ID")
            @PathVariable String commentId) {

        if (!currentUserProvider.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String userId = currentUserProvider.getCurrentUserId();
        solutionService.deleteComment(commentId, userId);
        return Result.success();
    }
}
