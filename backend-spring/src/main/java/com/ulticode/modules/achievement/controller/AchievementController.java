package com.ulticode.modules.achievement.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.achievement.dto.*;
import com.ulticode.modules.achievement.service.AchievementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for achievement operations.
 */
@Tag(name = "Achievement", description = "Achievement management API")
@RestController
@RequestMapping("/achievements")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer")
public class AchievementController {

    private final AchievementService achievementService;

    @Operation(summary = "Get all achievements")
    @GetMapping
    public Result<PageResult<AchievementVO>> list(AchievementQueryDTO query) {
        return Result.success(achievementService.list(query));
    }

    @Operation(summary = "Get achievement by ID")
    @GetMapping("/{id}")
    public Result<AchievementVO> getById(@PathVariable String id) {
        return Result.success(achievementService.getById(id));
    }

    @Operation(summary = "Get current user's achievement progress")
    @GetMapping("/user/me")
    public Result<List<AchievementProgressDTO>> getCurrentUserAchievements() {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(achievementService.getUserAchievements(userId));
    }

    @Operation(summary = "Get current user's achievement points")
    @GetMapping("/user/me/points")
    public Result<UserPointsVO> getCurrentUserPoints() {
        String userId = SecurityUtil.getCurrentUserId();
        return Result.success(achievementService.getUserPoints(userId));
    }

    // ========== Path Aliases for Frontend ==========

    @Operation(summary = "Alias for /achievements/user/me - get current user's achievements")
    @GetMapping("/my")
    public Result<List<AchievementProgressDTO>> getCurrentUserAchievementsAlias() {
        return getCurrentUserAchievements();
    }

    @Operation(summary = "Alias for /achievements/user/me/points - get current user's points")
    @GetMapping("/points")
    public Result<UserPointsVO> getCurrentUserPointsAlias() {
        return getCurrentUserPoints();
    }

    @Operation(summary = "Get a user's achievements by user ID", description = "Get achievement progress for any user by their ID")
    @GetMapping("/user/{id}")
    public Result<List<AchievementProgressDTO>> getUserAchievementsById(@PathVariable String id) {
        return Result.success(achievementService.getUserAchievements(id));
    }

    @Operation(summary = "Create achievement (admin only)")
    @RateLimit(key = "achievement:create", limit = 30, period = 60)
    @PostMapping
    public Result<AchievementVO> create(@Valid @RequestBody AchievementDTO dto) {
        return Result.success(achievementService.create(dto));
    }

    @Operation(summary = "Update achievement (admin only)")
    @RateLimit(key = "achievement:update", limit = 30, period = 60)
    @PutMapping("/{id}")
    public Result<AchievementVO> update(
            @PathVariable String id,
            @Valid @RequestBody AchievementDTO dto) {
        return Result.success(achievementService.update(id, dto));
    }

    @Operation(summary = "Delete achievement (admin only)")
    @RateLimit(key = "achievement:delete", limit = 30, period = 60)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        achievementService.delete(id);
        return Result.success();
    }
}
