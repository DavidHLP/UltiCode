package com.ulticode.modules.forum.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Forum Post Thread View Object for API responses.
 * Contains a post with its comment tree.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ForumPostThreadVO {

    /**
     * Post information
     */
    private ForumPostVO post;

    /**
     * Comments in hierarchical structure
     */
    private List<ForumCommentVO> comments;
}
