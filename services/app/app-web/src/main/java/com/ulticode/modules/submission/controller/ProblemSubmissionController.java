package com.ulticode.modules.submission.controller;

import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.submission.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.dto.RunSubmissionDTO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import com.ulticode.submission.api.service.SubmissionWritePort;
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
    private final SubmissionWritePort submissionWritePort;
    private final CodeExecutionService codeExecutionService;
    private final Validator validator;
    private final CurrentUserProvider currentUserProvider;

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
    public Result<PageResult<SubmissionListItemVO>> listProblemSubmissions(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId,
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false) Integer pageSize) {

        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }

        SubmissionQueryDTO query = new SubmissionQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);

        PageResult<SubmissionListItemVO> result = submissionService.findByProblemId(problemId, userId, query);
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

        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
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

        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }

        // Set problem ID from path, then validate
        createDTO.setProblemId(problemId);
        var violations = validator.validate(createDTO);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getMessage())
                    .findFirst()
                    .orElse("Validation failed");
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, message);
        }

        SubmissionVO submission = submissionWritePort.submit(userId, createDTO);
        return Result.success(submission);
    }

    @Operation(
            summary = "Run code",
            description = """
                    Execute user-submitted code against user-supplied test cases synchronously (no judging queue).

                    **Entry function name conventions** (extracted by `CodeExecutionHelperImpl.extractFunctionName`):
                    - JavaScript: `function name(...)` (function declaration; arrow functions `const x = () =>` are NOT detected)
                    - Python:     `def name(...)` or `class Solution: def method(...)`
                    - Java:       `class Solution { method }`
                    - C / C++:    full source (compiled then executed)

                    **Default entry name when no keyword match:** `solution`.

                    **Response fields** (`RunResultDTO`):
                    - `problemId`: `Long` (not string)
                    - `verdict`: top-level status (e.g. `"Accepted"`, `"Runtime Error"`)
                    - `runtime` / `memory`: pre-formatted strings (`"12ms"` / `"22.0MB"`)
                    - `runtimeMs` / `memoryMb`: numeric values (v2 schema, may be absent for legacy callers)
                    - `cases[]`: per-case results (each has its own `status`, `runtime`, `memory`)
                    """)
    @RateLimit(key = "submission:problem-run", limit = 30, period = 60)
    @PostMapping("/run")
    public Result<RunResultDTO> runCode(
            @Parameter(description = "Problem ID")
            @PathVariable Long problemId,
            @Valid @RequestBody RunSubmissionDTO runDTO) {

        String userId = currentUserProvider.getCurrentUserId();
        RunResultDTO result = codeExecutionService.execute(runDTO, problemId, userId);
        return Result.success(result);
    }
}
