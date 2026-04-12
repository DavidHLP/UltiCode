package com.ulticode.modules.submission.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.RunResultDTO;
import com.ulticode.modules.submission.dto.RunSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.service.CodeExecutionService;
import com.ulticode.modules.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for problem-specific submission operations.
 * These endpoints are under /api/problems/{problemId}/submissions
 */
@Tag(name = "Problem Submissions", description = "Problem-specific submission endpoints")
@RestController
@RequestMapping("/problems/{problemId}/submissions")
@RequiredArgsConstructor
public class ProblemSubmissionController {

    private final SubmissionService submissionService;
    private final CodeExecutionService codeExecutionService;
    private final Validator validator;

    /**
     * List submissions for a specific problem.
     * Requires authentication.
     *
     * @param problemId the problem ID
     * @param page      the page number (1-based)
     * @param pageSize  the number of items per page
     * @return paginated list of submissions
     */
    @Operation(summary = "List problem submissions", description = "Get paginated submissions for a specific problem")
    @GetMapping
    public Result<PageResult<SubmissionVO>> listProblemSubmissions(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false) Integer pageSize) {

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SubmissionQueryDTO query = new SubmissionQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);

        PageResult<SubmissionVO> result = submissionService.findByProblemId(problemId, userId, query);
        return Result.success(result);
    }

    /**
     * Get the best (fastest accepted) submission for a problem.
     * Requires authentication.
     *
     * @param problemId the problem ID
     * @return the best submission, or null if not found
     */
    @Operation(summary = "Get best submission", description = "Get the best accepted submission for a problem")
    @GetMapping("/best")
    public Result<SubmissionVO> getBestSubmission(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId) {

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SubmissionVO submission = submissionService.findBest(problemId, userId);
        return Result.success(submission);
    }

    /**
     * Submit code for a specific problem.
     * Requires authentication.
     *
     * @param problemId the problem ID
     * @param createDTO the submission data (language and code only)
     * @return the created submission
     */
    @Operation(summary = "Submit code", description = "Submit code for a specific problem")
    @RateLimit(key = "submission:problem-submit", limit = 20, period = 60)
    @PostMapping
    public Result<SubmissionVO> submitForProblem(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId,
            @RequestBody CreateSubmissionDTO createDTO) {

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // Set problem ID from path, then validate
        createDTO.setProblemId(problemId);
        var violations = validator.validate(createDTO);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getMessage())
                    .findFirst()
                    .orElse("Validation failed");
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }

        SubmissionVO submission = submissionService.submit(userId, createDTO);
        return Result.success(submission);
    }

    @Operation(summary = "Run code", description = "Execute code against test cases synchronously")
    @RateLimit(key = "submission:problem-run", limit = 30, period = 60)
    @PostMapping("/run")
    public Result<RunResultDTO> runCode(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId,
            @Valid @RequestBody RunSubmissionDTO runDTO) {

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        RunResultDTO result = codeExecutionService.execute(runDTO, problemId, userId);
        return Result.success(result);
    }
}
