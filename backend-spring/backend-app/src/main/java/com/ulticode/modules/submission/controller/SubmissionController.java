package com.ulticode.modules.submission.controller;

import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.LearningProgressDTO;
import com.ulticode.app.api.dto.SubmissionHistoryDTO;
import com.ulticode.app.api.dto.SubmissionQueryDTO;
import com.ulticode.app.api.dto.SubmissionDetailVO;
import com.ulticode.app.api.dto.SubmissionStatusMeta;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.service.SubmissionWritePort;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.service.SubmissionService;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for submission-related operations.
 */
@Tag(name = "Submissions", description = "Code submission endpoints")
@RestController
@RequestMapping("/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final SubmissionWritePort submissionWritePort;
    private final SubmissionProjection submissionProjection;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Submit code for a problem.
     * Requires authentication.
     *
     * @param createDTO the submission data
     * @return the created submission
     */
    @Operation(summary = "Submit code", description = "Submit code for a problem")
    @ApiResponse(responseCode = "200", description = "Submission created", content = @Content(schema = @Schema(implementation = SubmissionVO.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Problem not found")
    @RateLimit(key = "submission:create", limit = 20, period = 60)
    @PostMapping
    public Result<SubmissionVO> submit(@Valid @RequestBody CreateSubmissionDTO createDTO) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }

        SubmissionVO submission = submissionWritePort.submit(userId, createDTO);
        return Result.success(submission);
    }

    /**
     * Get a submission by ID.
     * Requires authentication.
     *
     * @param id the submission ID
     * @return the submission details
     */
    @Operation(summary = "Get submission by ID",
            description = """
                    Retrieve a specific submission's full detail.

                    **Distribution fields** (`runtimeDistBinsMs` / `memoryDistBinsMb`):
                    Serialized as a JSON array of integers, e.g. `[8, 16, 32, 64, 128, 256, 512]`.
                    Frontend should expect `number[]`, not a JSON string. Legacy callers
                    may still receive a JSON string in transitional windows; the frontend
                    `mapDistributionBins()` helper normalizes both shapes.

                    See `docs/reports/submission-api-test-report-2026-06-10.md` for the
                    full DTO contract.
                    """)
    @ApiResponse(responseCode = "200", description = "Submission retrieved", content = @Content(schema = @Schema(implementation = SubmissionVO.class)))
    @ApiResponse(responseCode = "403", description = "Not authorized to view this submission")
    @ApiResponse(responseCode = "404", description = "Submission not found")
    @GetMapping("/{id}")
    public Result<SubmissionDetailVO> getSubmission(
            @Parameter(description = "Submission ID")
            @PathVariable String id) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }

        SubmissionDetailVO submission = submissionService.findById(id, userId);
        return Result.success(submission);
    }

    /**
     * List user's submissions with pagination.
     * Requires authentication.
     *
     * @param page     the page number (1-based)
     * @param pageSize the number of items per page
     * @param problemId filter by problem ID
     * @return paginated list of submissions
     */
    @Operation(summary = "List user submissions", description = "Get paginated submissions for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Submissions retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping
    public Result<PageResult<SubmissionVO>> listUserSubmissions(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "Filter by problem ID")
            @RequestParam(required = false) Long problemId) {

        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }

        SubmissionQueryDTO query = new SubmissionQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setProblemId(problemId);

        PageResult<SubmissionVO> result = submissionService.findByUserId(userId, query);
        return Result.success(result);
    }

    /**
     * Get submission calendar (dates with submissions) for the authenticated user.
     * Requires authentication.
     *
     * @param year the year to filter by (defaults to current year)
     * @return list of date strings (YYYY-MM-DD) with submissions
     */
    @Operation(summary = "Get submission calendar", description = "Get dates with submissions for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Calendar retrieved", content = @Content(schema = @Schema(implementation = java.util.List.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/calendar")
    public Result<List<String>> getSubmissionCalendar(
            @Parameter(description = "Year to filter by")
            @RequestParam(required = false) Integer year) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }
        List<String> dates = submissionProjection.aggregateDates(userId, year);
        return Result.success(dates);
    }

    /**
     * Get the best (fastest accepted) submission for a problem.
     * Requires authentication.
     *
     * @param problemId the problem ID
     * @return the best submission, or null if not found
     */
    @Operation(summary = "Get best submission", description = "Get the best accepted submission for a problem")
    @ApiResponse(responseCode = "200", description = "Best submission retrieved", content = @Content(schema = @Schema(implementation = SubmissionVO.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "No accepted submission found")
    @GetMapping("/best")
    public Result<SubmissionVO> getBestSubmission(
            @Parameter(description = "Problem ID")
            @RequestParam Long problemId) {

        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }

        SubmissionVO submission = submissionService.findBest(problemId, userId);
        return Result.success(submission);
    }

    /**
     * Get learning progress for the authenticated user.
     * Requires authentication.
     *
     * @return learning progress data including weekly progress, difficulty breakdown, and streaks
     */
    @Operation(summary = "Get learning progress", description = "Get learning progress for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Learning progress retrieved", content = @Content(schema = @Schema(implementation = LearningProgressDTO.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/learning-progress")
    public Result<LearningProgressDTO> getLearningProgress() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }
        LearningProgressDTO progress = submissionProjection.aggregateLearningProgress(userId);
        return Result.success(progress);
    }

    /**
     * Get submission history for the authenticated user.
     * Requires authentication.
     *
     * @return submission history data including monthly stats and language breakdown
     */
    @Operation(summary = "Get submission history", description = "Get submission history for the authenticated user")
    @ApiResponse(responseCode = "200", description = "Submission history retrieved", content = @Content(schema = @Schema(implementation = SubmissionHistoryDTO.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/history")
    public Result<SubmissionHistoryDTO> getSubmissionHistory() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }
        SubmissionHistoryDTO history = submissionProjection.aggregateHistory(userId);
        return Result.success(history);
    }

    /**
     * Get available submission statuses for filtering and display.
     * This endpoint is public (no authentication required).
     *
     * @return list of submission status metadata
     */
    @Operation(summary = "Get submission statuses", description = "Get available submission status options")
    @ApiResponse(responseCode = "200", description = "Statuses retrieved", content = @Content(schema = @Schema(implementation = java.util.List.class)))
    @GetMapping("/statuses")
    public Result<List<SubmissionStatusMeta>> getSubmissionStatuses() {
        List<SubmissionStatusMeta> statuses = submissionProjection.getStatusCatalog();
        return Result.success(statuses);
    }
}
