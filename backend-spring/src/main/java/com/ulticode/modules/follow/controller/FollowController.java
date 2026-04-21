package com.ulticode.modules.follow.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.follow.dto.FollowStatusDTO;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.dto.UserSummaryDTO;
import com.ulticode.modules.follow.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for follow operations.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /**
     * Follow a user.
     */
    @PostMapping("/{id}/follow")
    public Result<FollowStatsDTO> follow(@PathVariable("id") String userId) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        FollowStatsDTO stats = followService.follow(currentUserId, userId);
        return Result.success(stats);
    }

    /**
     * Unfollow a user.
     */
    @DeleteMapping("/{id}/follow")
    public Result<FollowStatsDTO> unfollow(@PathVariable("id") String userId) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        FollowStatsDTO stats = followService.unfollow(currentUserId, userId);
        return Result.success(stats);
    }

    /**
     * Get paginated followers of a user.
     */
    @GetMapping("/{id}/followers")
    public Result<PageResult<UserSummaryDTO>> getFollowers(
            @PathVariable("id") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<UserSummaryDTO> result = followService.getFollowers(userId, page, pageSize);
        return Result.success(result);
    }

    /**
     * Get paginated following list of a user.
     */
    @GetMapping("/{id}/following")
    public Result<PageResult<UserSummaryDTO>> getFollowing(
            @PathVariable("id") String userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<UserSummaryDTO> result = followService.getFollowing(userId, page, pageSize);
        return Result.success(result);
    }

    /**
     * Check if the current user follows a specific user.
     */
    @GetMapping("/{id}/follow/status")
    public Result<FollowStatusDTO> getFollowStatus(@PathVariable("id") String userId) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        boolean isFollowing = followService.isFollowing(currentUserId, userId);
        FollowStatusDTO dto = new FollowStatusDTO();
        dto.setFollowing(isFollowing);
        return Result.success(dto);
    }
}
