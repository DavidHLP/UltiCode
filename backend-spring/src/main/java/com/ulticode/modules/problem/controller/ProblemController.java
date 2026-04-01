package com.ulticode.modules.problem.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.problem.dto.AdjacentProblemsVO;
import com.ulticode.modules.problem.dto.CreateProblemDTO;
import com.ulticode.modules.problem.dto.ProblemDetailResponse;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.service.ProblemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for problem-related operations.
 */
@Tag(name = "Problem", description = "Problem management endpoints")
@RestController
@RequestMapping("/problems")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

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
    @GetMapping
    public Result<PageResult<ProblemVO>> listProblems(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "Filter by difficulty")
            @RequestParam(required = false) String difficulty,
            @Parameter(description = "Filter by status")
            @RequestParam(required = false) String status,
            @Parameter(description = "Search by ID or title")
            @RequestParam(required = false) String search) {

        ProblemQueryDTO query = new ProblemQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setDifficulty(difficulty);
        query.setStatus(status);
        query.setSearch(search);

        PageResult<ProblemVO> result = problemService.listProblems(query);
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
    @GetMapping("/{id}")
    public Result<ProblemDetailResponse> getProblemById(
            @Parameter(description = "Problem ID")
            @PathVariable Long id) {
        ProblemDetailResponse problem = problemService.getProblemDetailResponse(id);
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
    @GetMapping("/slug/{slug}")
    public Result<ProblemDetailResponse> getProblemBySlug(
            @Parameter(description = "Problem slug")
            @PathVariable String slug) {
        ProblemDetailResponse problem = problemService.getProblemDetailResponseBySlug(slug);
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
    @GetMapping("/{id}/adjacent")
    public Result<AdjacentProblemsVO> getAdjacentProblems(
            @Parameter(description = "Current problem ID")
            @PathVariable Long id) {
        AdjacentProblemsVO adjacent = problemService.getAdjacentProblems(id);
        return Result.success(adjacent);
    }

    /**
     * Create a new problem.
     * Admin only - requires ADMIN or SUPER_ADMIN role.
     *
     * @param createDTO the create data
     * @return the created problem
     */
    @Operation(summary = "Create problem", description = "Create a new problem (admin only)")
    @PostMapping
    public Result<ProblemVO> createProblem(@Valid @RequestBody CreateProblemDTO createDTO) {
        // Admin role check
        if (!SecurityUtil.hasRole("ADMIN") && !SecurityUtil.hasRole("SUPER_ADMIN")) {
            return Result.error(40300, "Forbidden: Admin access required");
        }

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
    @PutMapping("/{id}")
    public Result<ProblemVO> updateProblem(
            @Parameter(description = "Problem ID")
            @PathVariable Long id,
            @Valid @RequestBody UpdateProblemDTO updateDTO) {
        // Admin role check
        if (!SecurityUtil.hasRole("ADMIN") && !SecurityUtil.hasRole("SUPER_ADMIN")) {
            return Result.error(40300, "Forbidden: Admin access required");
        }

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
    @DeleteMapping("/{id}")
    public Result<Void> deleteProblem(
            @Parameter(description = "Problem ID")
            @PathVariable Long id) {
        // Admin role check
        if (!SecurityUtil.hasRole("ADMIN") && !SecurityUtil.hasRole("SUPER_ADMIN")) {
            return Result.error(40300, "Forbidden: Admin access required");
        }

        problemService.deleteProblem(id);
        return Result.success();
    }
}
