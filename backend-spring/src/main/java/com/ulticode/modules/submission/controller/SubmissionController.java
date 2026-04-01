package com.ulticode.modules.submission.controller;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.LearningProgressDTO;
import com.ulticode.modules.submission.dto.SubmissionHistoryDTO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.service.SubmissionService;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    /**
     * Submit code for a problem.
     * Requires authentication.
     *
     * @param createDTO the submission data
     * @return the created submission
     */
    @Operation(summary = "Submit code", description = "Submit code for a problem")
    @PostMapping
    public Result<SubmissionVO> submit(@Valid @RequestBody CreateSubmissionDTO createDTO) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SubmissionVO submission = submissionService.submit(userId, createDTO);
        return Result.success(submission);
    }

    /**
     * Get a submission by ID.
     * Requires authentication.
     *
     * @param id the submission ID
     * @return the submission details
     */
    @Operation(summary = "Get submission by ID", description = "Retrieve a specific submission")
    @GetMapping("/{id}")
    public Result<SubmissionVO> getSubmission(
            @Parameter(description = "Submission ID")
            @PathVariable String id) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        SubmissionVO submission = submissionService.findById(id, userId);
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
    @GetMapping
    public Result<PageResult<SubmissionVO>> listUserSubmissions(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "Filter by problem ID")
            @RequestParam(required = false) Long problemId) {

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
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
    @GetMapping("/calendar")
    public Result<List<String>> getSubmissionCalendar(
            @Parameter(description = "Year to filter by")
            @RequestParam(required = false) Integer year) {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        List<String> dates = submissionService.getSubmissionDates(userId, year);
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
    @GetMapping("/best")
    public Result<SubmissionVO> getBestSubmission(
            @Parameter(description = "Problem ID")
            @RequestParam Long problemId) {

        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
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
    @GetMapping("/learning-progress")
    public Result<LearningProgressDTO> getLearningProgress() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        LearningProgressDTO progress = submissionService.getLearningProgress(userId);
        return Result.success(progress);
    }

    /**
     * Get submission history for the authenticated user.
     * Requires authentication.
     *
     * @return submission history data including monthly stats and language breakdown
     */
    @Operation(summary = "Get submission history", description = "Get submission history for the authenticated user")
    @GetMapping("/history")
    public Result<SubmissionHistoryDTO> getSubmissionHistory() {
        String userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        SubmissionHistoryDTO history = submissionService.getSubmissionHistory(userId);
        return Result.success(history);
    }
}
