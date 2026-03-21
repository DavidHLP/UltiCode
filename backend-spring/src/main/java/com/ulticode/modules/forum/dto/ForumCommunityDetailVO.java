package com.ulticode.modules.forum.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Forum Community Detail View Object for API responses.
 * Includes community information with rules and links.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForumCommunityDetailVO {

    /**
     * Community information
     */
    private ForumCommunityVO community;

    /**
     * Community rules
     */
    private List<CommunityRule> rules;

    /**
     * Community links
     */
    private List<CommunityLink> links;

    /**
     * Community rule nested class.
     */
    @Data
    public static class CommunityRule {
        /**
         * Rule unique identifier
         */
        private String id;

        /**
         * Rule title
         */
        private String title;

        /**
         * Rule description
         */
        private String description;

        /**
         * Sort order
         */
        private Integer sortOrder;

        /**
         * Creation timestamp
         */
        private LocalDateTime createdAt;
    }

    /**
     * Community link nested class.
     */
    @Data
    public static class CommunityLink {
        /**
         * Link unique identifier
         */
        private String id;

        /**
         * Link title
         */
        private String title;

        /**
         * Link URL
         */
        private String url;

        /**
         * Sort order
         */
        private Integer sortOrder;

        /**
         * Creation timestamp
         */
        private LocalDateTime createdAt;
    }
}
