package com.ulticode.modules.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ulticode.modules.user.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User profile view object for the profile page.
 * Aggregates user data, stats, and social counts in a single response.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileVO {

    private String id;
    private String username;
    private String name;
    private String avatar;
    private String bio;
    private String company;
    private String location;
    private String website;
    private LocalDateTime joinedAt;
    private String preferredLanguage;
    private int totalSolved;
    private Long submissionCount;
    private Integer globalRank;
    private Double acceptanceRate;
    private int followerCount;
    private int followingCount;
    private int achievementCount;

    /**
     * Factory method to build a ProfileVO from a User entity and aggregated stats.
     *
     * @param user             the user entity
     * @param stats            the user stats (may be null if unavailable)
     * @param followerCount    how many users follow this user
     * @param followingCount   how many users this user follows
     * @param achievementCount number of achievements earned
     * @return a new ProfileVO instance
     */
    public static ProfileVO fromUser(User user, UserStatsDTO stats,
                                     int followerCount, int followingCount, int achievementCount) {
        ProfileVO vo = new ProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setCompany(user.getCompany());
        vo.setLocation(user.getLocation());
        vo.setWebsite(user.getWebsite());
        vo.setJoinedAt(user.getJoinedAt());
        vo.setPreferredLanguage(user.getPreferredLanguage());
        vo.setTotalSolved(stats != null ? stats.getTotalSolved() : 0);
        vo.setSubmissionCount(stats != null ? stats.getSubmissionCount() : 0L);
        vo.setGlobalRank(stats != null ? stats.getGlobalRank() : null);
        vo.setAcceptanceRate(stats != null ? stats.getAcceptanceRate() : null);
        vo.setFollowerCount(followerCount);
        vo.setFollowingCount(followingCount);
        vo.setAchievementCount(achievementCount);
        return vo;
    }
}
