package com.ulticode.modules.user.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import com.ulticode.modules.user.dto.UserSkillsDTO;
import com.ulticode.modules.user.dto.UserStatsDTO;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user-related operations.
 */
@Tag(name = "User", description = "User management endpoints")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Get the current authenticated user's profile.
     *
     * @return the current user's profile
     */
    @Operation(summary = "Get current user", description = "Get the profile of the currently authenticated user")
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
    @RateLimit(key = "user:update", limit = 20, period = 60)
    @PatchMapping("/me")
    public Result<UserVO> updateCurrentUser(@Valid @RequestBody UpdateUserDTO updateDTO) {
        UserVO user = userService.updateCurrentUser(updateDTO);
        return Result.success(user);
    }

    /**
     * List users with pagination (public profiles).
     *
     * @param page     the page number (1-based)
     * @param pageSize the number of items per page
     * @return paginated list of users
     */
    @Operation(summary = "List users", description = "Get a paginated list of active users")
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
    @GetMapping("/{id}/skills")
    public Result<UserSkillsDTO> getUserSkills(
            @Parameter(description = "User ID")
            @PathVariable String id) {
        UserSkillsDTO skills = userService.getUserSkillsById(id);
        return Result.success(skills);
    }
}
