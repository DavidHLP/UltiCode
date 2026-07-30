package com.ulticode.modules.edgeoperations.controller;

import com.ulticode.websecurity.annotation.RateLimit;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationDTO;
import com.ulticode.modules.edgeoperations.dto.EdgeOperationResponseVO;
import com.ulticode.modules.edgeoperations.dto.GetInteractionsQueryDTO;
import com.ulticode.modules.edgeoperations.inspector.EdgeOperationInspector;
import com.ulticode.modules.edgeoperations.service.EdgeOperationsService;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for edge operations.
 * Handles operations like voting, analyzing, viewing, and retrieving interaction stats.
 */
@Tag(name = "Edge Operations", description = "Edge operations API for voting, analyzing, viewing content")
@RestController
@RequestMapping("/edge-operations")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class EdgeOperationsController {

    private final EdgeOperationsService edgeOperationsService;
    private final EdgeOperationInspector edgeOperationInspector;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Perform an edge operation",
            description = "Perform an edge operation (vote, analyze, view, etc.) on a target. " +
                    "For vote operations (VOTE_UP, VOTE_DOWN), delegates to vote service with toggle logic. " +
                    "For other operations, creates the operation if not exists, deletes if exists.")
    @RateLimit(key = "edge-operations:perform", limit = 20, period = 60)
    @PostMapping
    public Result<EdgeOperationResponseVO> performOperation(@Valid @RequestBody EdgeOperationDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(edgeOperationsService.performOperation(userId, dto));
    }

    @Operation(summary = "Get interaction stats for a target",
            description = "Returns likes, dislikes, favorites count, and the current user's vote status. " +
                    "Works for both authenticated and anonymous users.")
    @GetMapping("/interactions")
    public Result<EdgeOperationResponseVO> getInteractions(
            @Parameter(description = "Target ID") @RequestParam String targetId,
            @Parameter(description = "Target type") @RequestParam EdgeOperationTargetType targetType) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(edgeOperationInspector.getInteractions(userId, targetId, targetType));
    }

    @Operation(summary = "Get interaction stats for a target by path",
            description = "Returns likes, dislikes, favorites count, and the current user's vote status. " +
                    "Uses authenticated user if available, falls back to anonymous.")
    @GetMapping("/{targetType}/{targetId}")
    public Result<EdgeOperationResponseVO> getInteractionsByPath(
            @Parameter(description = "Target type") @PathVariable EdgeOperationTargetType targetType,
            @Parameter(description = "Target ID") @PathVariable String targetId) {
        String userId = currentUserProvider.getCurrentUserId();
        return Result.success(edgeOperationInspector.getInteractions(userId, targetId, targetType));
    }
}
