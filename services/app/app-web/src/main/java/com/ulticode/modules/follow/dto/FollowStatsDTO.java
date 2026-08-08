package com.ulticode.modules.follow.dto;

import lombok.Data;

/**
 * Follow statistics for a user.
 */
@Data
public class FollowStatsDTO {
    private int followerCount;
    private int followingCount;
}
