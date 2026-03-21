package com.ulticode.modules.solution.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.solution.dto.CreateSolutionDTO;
import com.ulticode.modules.solution.dto.SolutionVO;
import com.ulticode.modules.solution.dto.UpdateSolutionDTO;
import com.ulticode.modules.solution.service.SolutionService;
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
    public Result<PageResult<SolutionVO>> findByProblemId(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false) Integer pageSize) {

        PageResult<SolutionVO> result = solutionService.findByProblemId(problemId, page, pageSize);
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
    @PostMapping("/api/problems/{problemId}/solutions")
    public Result<SolutionVO> create(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId,
            @Valid @RequestBody CreateSolutionDTO createDTO) {

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(40100, "Unauthorized");
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
            @PathVariable String id) {

        SolutionVO solution = solutionService.getSolutionById(id);
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
    @PutMapping("/api/solutions/{id}")
    public Result<SolutionVO> update(
            @Parameter(description = "Solution ID")
            @PathVariable String id,
            @Valid @RequestBody UpdateSolutionDTO updateDTO) {

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(40100, "Unauthorized");
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
    @DeleteMapping("/api/solutions/{id}")
    public Result<Void> delete(
            @Parameter(description = "Solution ID")
            @PathVariable String id) {

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            return Result.error(40100, "Unauthorized");
        }

        solutionService.delete(id, userId);
        return Result.success();
    }
}
