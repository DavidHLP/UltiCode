package com.ulticode.modules.user.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.achievement.dto.AchievementProgressVO;
import com.ulticode.modules.achievement.service.AchievementService;
import com.ulticode.modules.user.dto.ChangePasswordDTO;
import com.ulticode.modules.user.dto.ProfileVO;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserSkillsDTO;
import com.ulticode.modules.user.dto.UserStatsDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for user-related operations.
 */
@Tag(name = "User", description = "User management endpoints")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AchievementService achievementService;

    /**
     * Get the current authenticated user's profile.
     *
     * @return the current user's profile
     */
    @Operation(summary = "Get current user", description = "Get the profile of the currently authenticated user")
    @ApiResponse(responseCode = "200", description = "Current user retrieved", content = @Content(schema = @Schema(implementation = UserVO.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        UserVO user = userService.getCurrentUser();
        return Result.success(user);
    }

    /**
     * Update the current authenticated user's profile.
     *
     * @param updateDTO the update data
     * @return the updated user profile
     */
    @Operation(summary = "Update current user", description = "Update the profile of the currently authenticated user")
    @ApiResponse(responseCode = "200", description = "User updated", content = @Content(schema = @Schema(implementation = UserVO.class)))
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @RateLimit(key = "user:update", limit = 20, period = 60)
    @PatchMapping("/me")
    public Result<UserVO> updateCurrentUser(@Valid @RequestBody UpdateUserDTO updateDTO) {
        UserVO user = userService.updateCurrentUser(updateDTO);
        return Result.success(user);
    }

    /**
     * Change the current authenticated user's password.
     *
     * @param changePasswordDTO the change password data
     * @return success result
     */
    @Operation(summary = "Change password", description = "Change the current user's password")
    @ApiResponse(responseCode = "200", description = "Password changed")
    @ApiResponse(responseCode = "400", description = "Validation error or incorrect current password")
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @RateLimit(key = "user:password", limit = 5, period = 60)
    @PatchMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        userService.changePassword(changePasswordDTO);
        return Result.success();
    }

    /**
     * List users with pagination (public profiles).
     *
     * @param page     the page number (1-based)
     * @param pageSize the number of items per page
     * @return paginated list of users
     */
    @Operation(summary = "List users", description = "Get a paginated list of active users")
    @ApiResponse(responseCode = "200", description = "Users retrieved", content = @Content(schema = @Schema(implementation = PageResult.class)))
    @GetMapping
    public Result<PageResult<UserVO>> listUsers(
            @Parameter(description = "Page number (1-based)")
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @Parameter(description = "Number of items per page")
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        PageResult<UserVO> result = userService.listUsers(page, pageSize);
        return Result.success(result);
    }

    /**
     * Get a user by ID (public profile).
     *
     * @param id the user ID
     * @return the user's public profile
     */
    @Operation(summary = "Get user by ID", description = "Get a user's public profile by their ID")
    @ApiResponse(responseCode = "200", description = "User retrieved", content = @Content(schema = @Schema(implementation = UserVO.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(
            @Parameter(description = "User ID")
            @PathVariable String id) {
        UserVO user = userService.getUserById(id);
        return Result.success(user);
    }

    /**
     * Get user statistics including solved problems count by difficulty,
     * streak, total solved, and submission heatmap.
     *
     * @param id the user ID
     * @return the user's statistics
     */
    @Operation(summary = "Get user stats", description = "Get user statistics including solved problems count by difficulty, streak, and heatmap")
    @ApiResponse(responseCode = "200", description = "Stats retrieved", content = @Content(schema = @Schema(implementation = UserStatsDTO.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{id}/stats")
    public Result<UserStatsDTO> getUserStats(
            @Parameter(description = "User ID")
            @PathVariable String id) {
        UserStatsDTO stats = userService.getUserStatsById(id);
        return Result.success(stats);
    }

    /**
     * Get user skills (tag statistics) for a user.
     *
     * @param id the user ID
     * @return the user's skills data
     */
    @Operation(summary = "Get user skills", description = "Get user skills including tag statistics for solved problems")
    @ApiResponse(responseCode = "200", description = "Skills retrieved", content = @Content(schema = @Schema(implementation = UserSkillsDTO.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{id}/skills")
    public Result<UserSkillsDTO> getUserSkills(
            @Parameter(description = "User ID")
            @PathVariable String id) {
        UserSkillsDTO skills = userService.getUserSkillsById(id);
        return Result.success(skills);
    }

    /**
     * Get a user's full profile including stats and social counts.
     *
     * @param id the user ID
     * @return the user profile view object
     */
    @Operation(summary = "Get user profile", description = "Get a user's full profile with stats and social counts")
    @ApiResponse(responseCode = "200", description = "Profile retrieved", content = @Content(schema = @Schema(implementation = ProfileVO.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/{id}/profile")
    public Result<ProfileVO> getUserProfile(
            @Parameter(description = "User ID")
            @PathVariable String id) {
        ProfileVO profile = userService.getUserProfile(id);
        return Result.success(profile);
    }

    /**
     * Get a user's profile by username.
     *
     * @param username the username
     * @return the user profile view object
     */
    @Operation(summary = "Get user profile by username", description = "Get a user's full profile by their username")
    @ApiResponse(responseCode = "200", description = "Profile retrieved", content = @Content(schema = @Schema(implementation = ProfileVO.class)))
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/by-username/{username}/profile")
    public Result<ProfileVO> getUserProfileByUsername(
            @Parameter(description = "Username")
            @PathVariable String username) {
        ProfileVO profile = userService.getUserProfileByUsername(username);
        return Result.success(profile);
    }

    /**
     * Upload the current user's avatar image.
     *
     * @param file the avatar image file
     * @return the URL of the uploaded avatar
     */
    @Operation(summary = "Upload avatar", description = "Upload and set the current user's avatar image")
    @ApiResponse(responseCode = "200", description = "Avatar uploaded", content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @RateLimit(key = "user:avatar", limit = 10, period = 60)
    @PostMapping("/me/avatar")
    public Result<String> uploadAvatar(
            @Parameter(description = "Avatar image file")
            @RequestParam("file") MultipartFile file) {
        String avatarUrl = userService.uploadAvatar(file);
        return Result.success(avatarUrl);
    }

    /**
     * Get the current user's achievement progress.
     *
     * @return list of achievement progress view objects
     */
    @Operation(summary = "Get achievement progress", description = "Get the current user's achievement progress for all achievements")
    @ApiResponse(responseCode = "200", description = "Achievement progress retrieved", content = @Content(schema = @Schema(implementation = java.util.List.class)))
    @ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/me/achievements/progress")
    public Result<List<AchievementProgressVO>> getAchievementProgress() {
        String userId = userService.getCurrentUser().getId();
        List<AchievementProgressVO> progress = achievementService.getUserProgress(userId);
        return Result.success(progress);
    }
}
