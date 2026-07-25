package com.ulticode.modules.follow.controller;

import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.follow.dto.FollowStatusDTO;
import com.ulticode.modules.follow.dto.FollowStatsDTO;
import com.ulticode.modules.follow.dto.UserSummaryDTO;
import com.ulticode.modules.follow.inspector.FollowInspector;
import com.ulticode.modules.follow.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for follow operations.
 *
 * <p>Splits across the two follow deep modules: writes (follow /
 * unfollow) go through {@link FollowService}, reads (followers /
 * following / follow-status) go through {@link FollowInspector} so the
 * read paths never pull in the write module's notification /
 * achievement collaborators.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;
    private final FollowInspector followInspector;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Follow a user.
     */
    @PostMapping("/{id}/follow")
    public Result<FollowStatsDTO> follow(@PathVariable("id") String userId) {
        String currentUserId = currentUserProvider.getCurrentUserId();
        FollowStatsDTO stats = followService.follow(currentUserId, userId);
        return Result.success(stats);
    }

    /**
     * Unfollow a user.
     */
    @DeleteMapping("/{id}/follow")
    public Result<FollowStatsDTO> unfollow(@PathVariable("id") String userId) {
        String currentUserId = currentUserProvider.getCurrentUserId();
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
        PageResult<UserSummaryDTO> result = followInspector.getFollowers(userId, page, pageSize);
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
        PageResult<UserSummaryDTO> result = followInspector.getFollowing(userId, page, pageSize);
        return Result.success(result);
    }

    /**
     * Check if the current user follows a specific user.
     */
    @GetMapping("/{id}/follow/status")
    public Result<FollowStatusDTO> getFollowStatus(@PathVariable("id") String userId) {
        String currentUserId = currentUserProvider.getCurrentUserId();
        boolean isFollowing = followInspector.isFollowing(currentUserId, userId);
        FollowStatusDTO dto = new FollowStatusDTO();
        dto.setFollowing(isFollowing);
        return Result.success(dto);
    }
}
