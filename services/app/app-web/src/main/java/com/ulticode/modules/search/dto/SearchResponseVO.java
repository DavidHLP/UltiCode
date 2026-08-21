package com.ulticode.modules.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Search response VO containing search results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Search response with results")
public class SearchResponseVO {

    /**
     * Original search query.
     */
    @Schema(description = "Original search query", example = "Two Sum")
    private String query;

    /**
     * Total number of matching results.
     */
    @Schema(description = "Total number of matching results", example = "100")
    private long total;

    /**
     * Current page number.
     */
    @Schema(description = "Current page number", example = "1")
    private int page;

    /**
     * Number of results per page.
     */
    @Schema(description = "Number of results per page", example = "20")
    private int limit;

    /**
     * List of search results.
     */
    @Schema(description = "List of search results")
    private List<SearchResultItem> results;

    /** Explicit mode/source/freshness/order/total facts for this response. */
    @Schema(description = "Read consistency semantics")
    private SearchReadSemantics semantics;

    /**
     * Individual search result item.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual search result item")
    public static class SearchResultItem {

        /**
         * ID of the entity.
         */
        @Schema(description = "ID of the entity", example = "1")
        private String id;

        /**
         * Type of the entity (PROBLEMS, USERS, POSTS, SOLUTIONS).
         */
        @Schema(description = "Type of the entity", example = "PROBLEMS")
        private String type;

        /**
         * Title of the entity.
         */
        @Schema(description = "Title of the entity", example = "Two Sum")
        private String title;

        /**
         * Description or summary.
         */
        @Schema(description = "Description or summary", example = "Find two numbers that add up to target")
        private String description;

        /**
         * URL to view the entity.
         */
        @Schema(description = "URL to view the entity", example = "/problems/two-sum")
        private String url;

        /**
         * Highlighted snippets from search.
         */
        @Schema(description = "Highlighted snippets from search")
        private Map<String, List<String>> highlights;

        /**
         * Additional metadata (optional).
         */
        @Schema(description = "Additional metadata")
        private Map<String, Object> metadata;
    }
}
