package com.ulticode.modules.user.controller;

import com.ulticode.app.error.UserErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.achievement.dto.AchievementProgressVO;
import com.ulticode.modules.achievement.projection.AchievementProjection;
import com.ulticode.modules.user.dto.ProfileVO;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserSkillsDTO;
import com.ulticode.modules.user.dto.UserStatsDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.port.AppUserWritePort;
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.websecurity.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user-related operations (App-owned surface).
 *
 * <p>P7-RELOCATE-USER-REMAINDER-001: relocated from backend-legacy. The
 * {@code /me/password} endpoint stays in the legacy controller because
 * password mutation is Auth-owned; the remaining 10 endpoints are
 * App-owned reads and profile mutations.
 */
@Tag(name = "User", description = "User management endpoints")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserReadProjection userReadProjection;
    private final AppUserWritePort appUserWritePort;
    private final AchievementProjection achievementProjection;
    private final CurrentUserProvider currentUserProvider;

    @Operation(summary = "Get current user", description = "Get the profile of the currently authenticated user")
    @ApiResponse(responseCode = "200", description = "Current user retrieved", content = @Content(schema = @Schema(implementation = UserVO.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        UserVO user = userReadProjection.getCurrentUser();
        return Result.success(user);
    }

    @Operation(summary = "Update current user", description = "Update the profile of the currently authenticated user")
    @ApiResponse(responseCode = "200", description = "User updated", content = @Content(schema = @Schema(implementation = UserVO.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @RateLimit(key = "user:update", limit = 20, period = 60)
    @PatchMapping("/me")
    public Result<UserVO> updateCurrentUser(@Valid @RequestBody UpdateUserDTO updateDTO) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }
        UserVO user = appUserWritePort.updateProfile(userId, updateDTO);
        return Result.success(user);
    }

    @Operation(summary = "List users", description = "Get a paginated list of active users")
    @ApiResponse(responseCode = "200", description = "Users retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @GetMapping
    public Result<PageResult<UserVO>> listUsers(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        PageResult<UserVO> result = userReadProjection.listUsers(page, pageSize);
        return Result.success(result);
    }

    @Operation(summary = "Get user by ID", description = "Get a user's public profile by their ID")
    @ApiResponse(responseCode = "200", description = "User retrieved", content = @Content(schema = @Schema(implementation = UserVO.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(
            @Parameter(description = "User ID")
            @PathVariable String id) {
        UserVO user = userReadProjection.getUserById(id);
        return Result.success(user);
    }

    @Operation(summary = "Get user stats", description = "Get user statistics including solved problems count by difficulty, streak, and heatmap")
    @ApiResponse(responseCode = "200", description = "Stats retrieved", content = @Content(schema = @Schema(implementation = UserStatsDTO.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{id}/stats")
    public Result<UserStatsDTO> getUserStats(
            @Parameter(description = "User ID")
            @PathVariable String id) {
        UserStatsDTO stats = userReadProjection.getUserStatsById(id);
        return Result.success(stats);
    }

    @Operation(summary = "Get user skills", description = "Get user skills including tag statistics for solved problems")
    @ApiResponse(responseCode = "200", description = "Skills retrieved", content = @Content(schema = @Schema(implementation = UserSkillsDTO.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{id}/skills")
    public Result<UserSkillsDTO> getUserSkills(
            @Parameter(description = "User ID")
            @PathVariable String id) {
        UserSkillsDTO skills = userReadProjection.getUserSkillsById(id);
        return Result.success(skills);
    }

    @Operation(summary = "Get user profile", description = "Get a user's full profile with stats and social counts")
    @ApiResponse(responseCode = "200", description = "Profile retrieved", content = @Content(schema = @Schema(implementation = ProfileVO.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{id}/profile")
    public Result<ProfileVO> getUserProfile(
            @Parameter(description = "User ID")
            @PathVariable String id) {
        ProfileVO profile = userReadProjection.getUserProfile(id);
        return Result.success(profile);
    }

    @Operation(summary = "Get user profile by username", description = "Get a user's full profile by their username")
    @ApiResponse(responseCode = "200", description = "Profile retrieved", content = @Content(schema = @Schema(implementation = ProfileVO.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/by-username/{username}/profile")
    public Result<ProfileVO> getUserProfileByUsername(
            @Parameter(description = "Username")
            @PathVariable String username) {
        ProfileVO profile = userReadProjection.getUserProfileByUsername(username);
        return Result.success(profile);
    }

    @Operation(summary = "Upload avatar", description = "Upload and set the current user's avatar image")
    @ApiResponse(responseCode = "200", description = "Avatar uploaded", content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @RateLimit(key = "user:avatar", limit = 10, period = 60)
    @PostMapping("/me/avatar")
    public Result<String> uploadAvatar(
            @Parameter(description = "Avatar image file")
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED);
        }
        String avatarUrl = appUserWritePort.uploadAvatar(userId, file);
        return Result.success(avatarUrl);
    }

    @Operation(summary = "Get achievement progress", description = "Get the current user's achievement progress for all achievements")
    @ApiResponse(responseCode = "200", description = "Achievement progress retrieved", content = @Content(schema = @Schema(implementation = List.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/me/achievements/progress")
    public Result<List<AchievementProgressVO>> getAchievementProgress() {
        String userId = userReadProjection.getCurrentUser().getId();
        List<AchievementProgressVO> progress = achievementProjection.getUserProgress(userId);
        return Result.success(progress);
    }
}
