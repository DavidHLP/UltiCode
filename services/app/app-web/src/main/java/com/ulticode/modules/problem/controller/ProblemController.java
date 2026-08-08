package com.ulticode.modules.problem.controller;

import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.problem.dto.AdjacentProblemsVO;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemDetailPublicVO;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.projection.ProblemProjection;
import com.ulticode.modules.problem.service.ProblemService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.app.error.ProblemErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for problem-related operations.
 */
@Tag(name = "Problem", description = "Problem management endpoints")
@Validated
@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;
    private final ProblemProjection problemProjection;

    /**
     * List problems with pagination and filters.
     * Public endpoint - accessible without authentication.
     *
     * @param page     the page number (1-based)
     * @param pageSize the number of items per page
     * @param difficulty filter by difficulty
     * @param status     filter by status
     * @param search     search by ID or title
     * @return paginated list of problems
     */
    @Operation(summary = "List problems", description = "Get a paginated list of problems with optional filters")
    @ApiResponse(responseCode = "200", description = "Problems retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @GetMapping
    public Result<PageResult<ProblemVO>> listProblems(@Validated @ModelAttribute ProblemQueryDTO query) {
        PageResult<ProblemVO> result = problemProjection.listProblems(query);
        return Result.success(result);
    }

    /**
     * Get a problem by ID.
     * Public endpoint - accessible without authentication.
     *
     * @param id the problem ID
     * @return the problem details
     */
    @Operation(summary = "Get problem by ID", description = "Get a problem's details by its ID")
    @ApiResponse(responseCode = "200", description = "Problem retrieved", content = @Content(schema = @Schema(implementation = ProblemDetailPublicVO.class)))
    @ApiResponse(responseCode = "404", description = "Problem not found")
    @GetMapping("/{id}")
    public Result<ProblemDetailPublicVO> getProblemById(
            @Parameter(description = "Problem ID")
            @PathVariable String id) {
        // D-14: non-numeric id under /{id} path translates to 404 (not 400) so frontend can
        // reuse the same "PROBLEM_NOT_FOUND" toast for both /abc and /99999.
        Long problemId;
        try {
            problemId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BusinessException(ProblemErrorCode.PROBLEM_NOT_FOUND);
        }
        ProblemDetailPublicVO problem = problemProjection.publicDetailById(problemId);
        return Result.success(problem);
    }

    /**
     * Get a problem by slug.
     * Public endpoint - accessible without authentication.
     *
     * @param slug the problem slug
     * @return the problem details
     */
    @Operation(summary = "Get problem by slug", description = "Get a problem's details by its slug")
    @ApiResponse(responseCode = "200", description = "Problem retrieved", content = @Content(schema = @Schema(implementation = ProblemDetailPublicVO.class)))
    @ApiResponse(responseCode = "404", description = "Problem not found")
    @GetMapping("/slug/{slug}")
    public Result<ProblemDetailPublicVO> getProblemBySlug(
            @Parameter(description = "Problem slug")
            @PathVariable String slug) {
        ProblemDetailPublicVO problem = problemProjection.publicDetailBySlug(slug);
        return Result.success(problem);
    }

    /**
     * Get adjacent problems for navigation (prev/next).
     * Public endpoint - accessible without authentication.
     *
     * @param id the current problem ID
     * @return the previous and next problem slugs
     */
    @Operation(summary = "Get adjacent problems", description = "Get previous and next problem slugs for navigation")
    @ApiResponse(responseCode = "200", description = "Adjacent problems retrieved", content = @Content(schema = @Schema(implementation = AdjacentProblemsVO.class)))
    @ApiResponse(responseCode = "404", description = "Problem not found")
    @GetMapping("/{id}/adjacent")
    public Result<AdjacentProblemsVO> getAdjacentProblems(
            @Parameter(description = "Current problem ID")
            @PathVariable Long id) {
        AdjacentProblemsVO adjacent = problemProjection.adjacentProblems(id);
        return Result.success(adjacent);
    }

    /**
     * Get a random published problem.
     * Public endpoint - accessible without authentication.
     *
     * @return a random published problem
     */
    @Operation(summary = "Get a random published problem")
    @ApiResponse(responseCode = "200", description = "Random problem retrieved", content = @Content(schema = @Schema(implementation = ProblemVO.class)))
    @ApiResponse(responseCode = "404", description = "No published problems available")
    @GetMapping("/random")
    public Result<ProblemVO> getRandomProblem() {
        return Result.success(problemProjection.findRandomPublished());
    }

    /**
     * Create a new problem.
     * Admin only - requires ADMIN or SUPER_ADMIN role.
     *
     * @param createDTO the create data
     * @return the created problem
     */
    @Operation(summary = "Create problem", description = "Create a new problem (admin only)")
    @ApiResponse(responseCode = "200", description = "Problem created", content = @Content(schema = @Schema(implementation = ProblemVO.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "403", description = "Not authorized - admin only")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "problem:create", limit = 30, period = 60)
    @PostMapping
    public Result<ProblemVO> createProblem(@Valid @RequestBody CreateProblemDTO createDTO) {
        ProblemVO problem = problemService.createProblem(createDTO);
        return Result.success(problem);
    }

    /**
     * Update an existing problem.
     * Admin only - requires ADMIN or SUPER_ADMIN role.
     *
     * @param id        the problem ID
     * @param updateDTO the update data
     * @return the updated problem
     */
    @Operation(summary = "Update problem", description = "Update an existing problem (admin only)")
    @ApiResponse(responseCode = "200", description = "Problem updated", content = @Content(schema = @Schema(implementation = ProblemVO.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "403", description = "Not authorized - admin only")
    @ApiResponse(responseCode = "404", description = "Problem not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "problem:update", limit = 30, period = 60)
    @PutMapping("/{id}")
    public Result<ProblemVO> updateProblem(
            @Parameter(description = "Problem ID")
            @PathVariable Long id,
            @Valid @RequestBody UpdateProblemDTO updateDTO) {
        ProblemVO problem = problemService.updateProblem(id, updateDTO);
        return Result.success(problem);
    }

    /**
     * Delete a problem (soft delete).
     * Admin only - requires ADMIN or SUPER_ADMIN role.
     *
     * @param id the problem ID
     * @return success result
     */
    @Operation(summary = "Delete problem", description = "Delete a problem (admin only)")
    @ApiResponse(responseCode = "200", description = "Problem deleted")
    @ApiResponse(responseCode = "403", description = "Not authorized - admin only")
    @ApiResponse(responseCode = "404", description = "Problem not found")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "problem:delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    public Result<Void> deleteProblem(
            @Parameter(description = "Problem ID")
            @PathVariable Long id) {
        problemService.deleteProblem(id);
        return Result.success();
    }
}
