package com.ulticode.modules.contest.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.response.Result;
import com.ulticode.modules.contest.controller.internal.ContestControllerSupport;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.ParticipationStatusDTO;
import com.ulticode.modules.contest.dto.UserContestHistoryVO;
import com.ulticode.modules.contest.projection.ContestProjection;
import com.ulticode.modules.contest.service.ContestService;
import com.ulticode.modules.contest.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Contest participation state machine:
 * register, unregister, check-in, virtual-*, participation, my-contests, history.
 *
 * <p>Every endpoint here mutates or reads the (contest, user) join state, so
 * rate limits and auth scopes are applied uniformly. Virtual contest lives here
 * as its own sub-machine within the participation module.
 */
@Tag(name = "Contest Participation", description = "Authenticated contest participation endpoints")
@RestController
@RequestMapping("/contest")
@RequiredArgsConstructor
public class ContestParticipationController {

    private final ContestService contestService;
    private final ContestProjection contestProjection;
    private final RankingService rankingService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Register for contest",
            description = "Register the current user for a contest")
    @ApiResponse(responseCode = "200", description = "Registration successful")
    @ApiResponse(responseCode = "400", description = "Already registered or contest not open")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:register", limit = 20, period = 60)
    @PostMapping("/{id}/register")
    public Result<Void> registerForContest(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        contestService.registerForContest(resolvedId, ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider));
        return Result.success();
    }

    @Operation(summary = "Check in to contest (alias for register)",
            description = "Currently delegates to register. Same business rules apply.")
    @ApiResponse(responseCode = "200", description = "Check-in successful")
    @ApiResponse(responseCode = "400", description = "Already registered or contest not open")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:check-in", limit = 20, period = 60)
    @PostMapping("/{id}/check-in")
    public Result<Void> checkIn(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        contestService.registerForContest(resolvedId, ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider));
        return Result.success();
    }

    @Operation(summary = "Unregister from contest")
    @ApiResponse(responseCode = "200", description = "Unregistration successful")
    @ApiResponse(responseCode = "400", description = "Not registered for this contest")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:unregister", limit = 20, period = 60)
    @DeleteMapping("/{id}/register")
    public Result<Void> unregisterFromContest(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        contestService.unregisterFromContest(resolvedId, ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider));
        return Result.success();
    }

    @Operation(summary = "Get participation status")
    @ApiResponse(responseCode = "200", description = "Participation status retrieved")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/{id}/participation")
    public Result<ParticipationStatusDTO> getParticipationStatus(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        return Result.success(contestService.getParticipationStatus(
                resolvedId, ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider)));
    }

    @Operation(summary = "Start virtual contest",
            description = "Start a virtual participation for a past contest. Idempotent on ACTIVE sessions; concurrent calls are serialized.")
    @ApiResponse(responseCode = "200", description = "Virtual contest started")
    @ApiResponse(responseCode = "400", description = "Cannot start virtual contest for non-past contest")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Contest not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:virtual-start:{id}", limit = 20, period = 60)
    @PostMapping("/{id}/virtual/start")
    public Result<ParticipationStatusDTO> startVirtualContest(@PathVariable String id) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        return Result.success(contestService.startVirtualContest(
                resolvedId, ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider)));
    }

    @Operation(summary = "Get virtual session")
    @ApiResponse(responseCode = "200", description = "Virtual session retrieved")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Virtual session not found")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/{id}/virtual/session")
    public Result<ParticipationStatusDTO> getVirtualSession(
            @PathVariable String id,
            @Parameter(description = "Virtual session ID (optional since 2026-06-11)")
            @RequestParam(required = false) String sessionId) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        return Result.success(contestService.getVirtualSession(
                resolvedId, ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider)));
    }

    @Operation(summary = "Finish virtual contest")
    @ApiResponse(responseCode = "200", description = "Virtual contest finished")
    @ApiResponse(responseCode = "400", description = "No active virtual session or session id mismatch")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @ApiResponse(responseCode = "404", description = "Virtual session not found")
    @SecurityRequirement(name = "Bearer")
    @RateLimit(key = "contest:virtual-finish:{id}", limit = 20, period = 60)
    @PostMapping("/{id}/virtual/finish")
    public Result<Void> finishVirtualContest(
            @PathVariable String id,
            @RequestParam(required = false) String sessionId) {
        String resolvedId = ContestControllerSupport.resolveContestId(contestProjection, id);
        contestService.finishVirtualContest(
                resolvedId, sessionId, ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider));
        return Result.success();
    }

    @Operation(summary = "Get my contests")
    @ApiResponse(responseCode = "200", description = "User's contests retrieved")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/user/my-contests")
    public Result<List<ContestVO>> getMyContests(
            @Parameter(description = "Type of contests (registered, participated, virtual)")
            @RequestParam(required = false, defaultValue = "participated") String type) {
        return Result.success(contestService.getUserContests(
                ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider), type));
    }

    @Operation(summary = "Get contest history")
    @ApiResponse(responseCode = "200", description = "Contest history retrieved")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/user/history")
    public Result<List<UserContestHistoryVO>> getContestHistory() {
        return Result.success(rankingService.getUserContestHistory(
                ContestControllerSupport.getCurrentUserIdOrThrow(currentUserProvider)));
    }
}
