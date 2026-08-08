package com.ulticode.modules.vote.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VO for vote operation results.
 * Returns the current vote counts and the user's vote status.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Vote result VO")
public class VoteResultVO {

    /**
     * ID of the target
     */
    @Schema(description = "ID of the target")
    private String targetId;

    /**
     * Type of the target
     */
    @Schema(description = "Type of the target")
    private String targetType;

    /**
     * Number of upvotes (likes)
     */
    @Schema(description = "Number of upvotes (likes)")
    private long likes;

    /**
     * Number of downvotes (dislikes)
     */
    @Schema(description = "Number of downvotes (dislikes)")
    private long dislikes;

    /**
     * Current user's vote: 1 (upvoted), -1 (downvoted), 0 (no vote)
     */
    @Schema(description = "Current user's vote: 1 (upvoted), -1 (downvoted), 0 (no vote)")
    private int userVote;
}
