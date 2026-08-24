package com.ulticode.modules.solution.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lightweight solution list item DTO.
 * Excludes content field for efficient list queries.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SolutionListItemVO {

    private String id;
    private Long problemId;
    private String title;
    private String summary;
    private String language;
    private List<String> tags;

    /**
     * Author information
     */
    private AuthorInfo author;

    /**
     * Interaction counts
     */
    private Counts counts;

    private Long score;

    /**
     * Current viewer's vote: 1 = upvote, -1 = downvote, 0 = no vote
     */
    private Integer viewerVote;

    private LocalDateTime publishedAt;
    private Boolean isPinned;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthorInfo {
        private String id;
        private String username;
        private String name;
        private String avatar;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Counts {
        private Integer views;
        private Long comments;
        private Long likes;
        private Long dislikes;
    }
}
