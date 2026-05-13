package com.ulticode.modules.contest.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.Result;
import com.ulticode.modules.contest.dto.*;
import com.ulticode.modules.contest.service.ScoringRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Scoring Rules", description = "Scoring rule management endpoints")
@RestController
@RequestMapping("/admin/scoring-rules")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class ScoringRuleController {

    private final ScoringRuleService scoringRuleService;

    @Operation(summary = "Get all scoring rules")
    @ApiResponse(responseCode = "200", description = "Scoring rules retrieved", content = @Content(schema = @Schema(implementation = List.class)))
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<List<ScoringRuleVO>> getAllScoringRules(
            @Parameter(description = "Include inactive rules")
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        return Result.success(scoringRuleService.findAll(includeInactive));
    }

    @Operation(summary = "Get scoring rule by ID")
    @ApiResponse(responseCode = "200", description = "Scoring rule retrieved", content = @Content(schema = @Schema(implementation = ScoringRuleVO.class)))
    @ApiResponse(responseCode = "404", description = "Scoring rule not found")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Result<ScoringRuleVO> getScoringRuleById(
            @Parameter(description = "Scoring rule ID")
            @PathVariable String id) {
        return Result.success(scoringRuleService.findById(id));
    }

    @Operation(summary = "Create scoring rule")
    @ApiResponse(responseCode = "200", description = "Scoring rule created", content = @Content(schema = @Schema(implementation = ScoringRuleVO.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "scoring-rule:create", limit = 30, period = 60)
    public Result<ScoringRuleVO> createScoringRule(
            @Valid @RequestBody CreateScoringRuleDTO dto) {
        return Result.success(scoringRuleService.create(dto));
    }

    @Operation(summary = "Update scoring rule")
    @ApiResponse(responseCode = "200", description = "Scoring rule updated", content = @Content(schema = @Schema(implementation = ScoringRuleVO.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "404", description = "Scoring rule not found")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "scoring-rule:update", limit = 30, period = 60)
    public Result<ScoringRuleVO> updateScoringRule(
            @Parameter(description = "Scoring rule ID")
            @PathVariable String id,
            @Valid @RequestBody UpdateScoringRuleDTO dto) {
        return Result.success(scoringRuleService.update(id, dto));
    }

    @Operation(summary = "Delete scoring rule")
    @ApiResponse(responseCode = "200", description = "Scoring rule deleted")
    @ApiResponse(responseCode = "400", description = "Rule is in use by contests")
    @ApiResponse(responseCode = "404", description = "Scoring rule not found")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @RateLimit(key = "scoring-rule:delete", limit = 30, period = 60)
    public Result<Void> deleteScoringRule(
            @Parameter(description = "Scoring rule ID")
            @PathVariable String id) {
        scoringRuleService.delete(id);
        return Result.success();
    }
}
