package com.ulticode.modules.edgeoperations.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * VO for edge operation response.
 * Contains vote counts, favorites count, and the viewer's interaction state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Edge operation response VO")
public class EdgeOperationResponseVO {

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
     * Number of favorites/bookmarks
     */
    @Schema(description = "Number of users who favorited/bookmarked this item")
    private long favorites;

    /**
     * Information about the current viewer's interactions
     */
    @Schema(description = "Current viewer's interaction state")
    private ViewerState viewer;

    /**
     * Inner class representing the viewer's interaction state
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Viewer's interaction state")
    public static class ViewerState {

        /**
         * Current user's vote: 1 (upvoted), -1 (downvoted), 0 (no vote)
         */
        @Schema(description = "Current user's vote: 1 (upvoted), -1 (downvoted), 0 (no vote)")
        private int vote;
    }
}
