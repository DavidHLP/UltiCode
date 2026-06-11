package com.ulticode.modules.follow.dto;

import lombok.Data;

/**
 * DTO for follow status check between current user and a target user.
 */
@Data
public class FollowStatusDTO {
    private boolean following;
}