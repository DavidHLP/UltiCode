package com.ulticode.modules.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of bulk action operation on forum posts.
 */
@Data
@Schema(description = "Result of bulk action on forum posts")
public class BulkActionResult {

    @Schema(description = "List of action results for each post")
    private List<BulkActionItem> results;

    @Schema(description = "Total number of posts processed")
    private Integer total;

    @Schema(description = "Number of successful actions")
    private Integer successful;

    @Schema(description = "Number of failed actions")
    private Integer failed;

    /**
     * Individual action result for a single post.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Result of action on a single post")
    public static class BulkActionItem {

        @Schema(description = "Post ID")
        private String id;

        @Schema(description = "Whether the action succeeded")
        private Boolean success;

        @Schema(description = "Error message if action failed")
        private String error;
    }
}
