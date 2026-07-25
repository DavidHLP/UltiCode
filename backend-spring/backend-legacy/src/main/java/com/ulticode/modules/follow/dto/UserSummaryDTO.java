package com.ulticode.modules.follow.dto;

import lombok.Data;

/**
 * Summary of a user for follow lists.
 */
@Data
public class UserSummaryDTO {
    private String id;
    private String username;
    private String avatar;
    private String bio;
    private int followerCount;
    private int followingCount;
}
