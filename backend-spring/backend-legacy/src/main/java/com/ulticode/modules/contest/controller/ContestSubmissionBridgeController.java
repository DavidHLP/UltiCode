package com.ulticode.modules.contest.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.Result;
import com.ulticode.modules.contest.controller.internal.ContestControllerSupport;
import com.ulticode.modules.contest.projection.ContestProjection;
import com.ulticode.modules.contest.service.ContestService;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Bridge that lets the contest module expose problem-submission endpoints
 * without owning the submission module. The contest module resolves the
 * problem id (its own concern) and then delegates to
 * {@link ContestService}, which in turn calls the submission service.
 *
 * <p>Originally these endpoints lived in the 710-LoC ContestController god
 * class and reached past the service to a mapper. They now stay in the
 * contest namespace (so the URL surface is unchanged) but no longer touch
 * the persistence seam directly.
 */
@Tag(name = "Contest Submission Bridge",
        description = "Contest-scoped problem submissions (delegates to submission service)")
@RestController
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestSubmissionBridgeController {

    private final ContestService contestService;
    private final ContestProjection contestProjection;
    private final Validator validator;
    private final CurrentUserProvider currentUserProvider;

    private static final String MSG_PROBLEM_ID_REQUIRED = "Problem id is required";

    @Operation(summary = "Get contest problem submissions",
            description = "Get the current user's submissions for a problem in a contest")
    @ApiResponse(responseCode = "200", description = "Contest problem submissions retrieved")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest or problem not found")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/{id}/problems/{problemId}/submissions")
    public Result<List<SubmissionVO>> getContestProblemSubmissions(
            @PathVariable String id,
            @PathVariable Long problemId) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        String userId = ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider);
        return Result.success(contestProjection.getContestProblemSubmissions(resolvedId, problemId, userId));
    }

    @Operation(summary = "Submit contest problem",
            description = "Submit code for a problem in a contest")
    @ApiResponse(responseCode = "200", description = "Contest problem submitted")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest or problem not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:problem-submit", limit = 20, period = 60)
    @PostMapping("/{id}/problems/{problemId}/submissions")
    public Result<SubmissionVO> submitContestProblem(
            @PathVariable String id,
            // Path variable name kept as {problemId} for API compatibility;
            // Java parameter renamed to `problemPath` since it accepts both a
            // numeric id (e.g., "1") and a composite id (e.g., "cp-u1-A").
            @Parameter(description = "Problem identifier (numeric id or contest_problem id)")
            @PathVariable("problemId") String problemPath,
            @RequestBody CreateSubmissionDTO createDTO) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        String userId = ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider);
        if (createDTO == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Submission payload is required");
        }
        Long realProblemId = resolveContestProblemId(resolvedId, problemPath);
        createDTO.setProblemId(realProblemId);
        validateSubmissionPayload(createDTO);
        return Result.success(contestService.submitContestProblem(
                resolvedId, realProblemId, userId, createDTO));
    }

    private Long resolveContestProblemId(String contestId, String problemPath) {
        if (problemPath == null || problemPath.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, MSG_PROBLEM_ID_REQUIRED);
        }
        return contestProjection.resolveContestProblemId(contestId, problemPath);
    }

    private void validateSubmissionPayload(CreateSubmissionDTO createDTO) {
        Set<ConstraintViolation<CreateSubmissionDTO>> violations = validator.validate(createDTO);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .findFirst()
                    .orElse("Validation failed");
            throw new BusinessException(ErrorCode.BAD_REQUEST, message);
        }
    }
}
