package com.ulticode.modules.vote.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.vote.dto.VoteDTO;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.service.VoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for vote operations.
 * Handles upvoting, downvoting, and retrieving vote counts.
 */
@Tag(name = "Vote", description = "Vote management API")
@RestController
@RequestMapping("/vote")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class VoteController {

    private final VoteService voteService;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Vote on a target item",
            description = "Three-state voting: 1 (upvote), -1 (downvote), 0 (neutral/remove vote). " +
                    "Clicking the same vote type toggles it off.")
    @RateLimit(key = "vote:cast", limit = 20, period = 60)
    @PostMapping
    public Result<VoteResultVO> vote(@Valid @RequestBody VoteDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(voteService.vote(userId, dto));
    }

    @Operation(summary = "Get vote status for a target",
            description = "Returns the current vote counts and the authenticated user's vote status")
    @GetMapping("/{targetType}/{targetId}")
    public Result<VoteResultVO> getVoteStatus(
            @Parameter(description = "Target type") @PathVariable EdgeOperationTargetType targetType,
            @Parameter(description = "Target ID") @PathVariable String targetId) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(voteService.getVoteStatus(userId, targetId, targetType));
    }
}
