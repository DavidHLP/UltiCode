package com.ulticode.modules.forum.service;

import com.ulticode.modules.forum.dto.CreateCommentDTO;
import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.dto.UpdateCommentDTO;

import java.util.List;
import java.util.Map;

/**
 * Service interface for forum comment operations.
 */
public interface ForumCommentService {

    /**
     * Create a new comment.
     *
     * @param postId the post ID
     * @param dto    the create comment DTO
     * @param userId the author user ID
     * @return the created comment
     */
    ForumCommentVO createComment(String postId, CreateCommentDTO dto, String userId);

    /**
     * Update an existing comment.
     *
     * @param id     the comment ID
     * @param dto    the update comment DTO
     * @param userId the user ID making the update
     * @return the updated comment
     */
    ForumCommentVO updateComment(String id, UpdateCommentDTO dto, String userId);

    /**
     * Delete a comment (soft delete).
     *
     * @param id     the comment ID
     * @param userId the user ID making the delete
     */
    void deleteComment(String id, String userId);

    /**
     * Build comment tree from a flat list of comments.
     *
     * @param comments  the flat list of comments
     * @param authorMap map of author ID to User entity
     * @return hierarchical list of comment VOs with nested replies
     */
    List<ForumCommentVO> buildCommentTree(List<? extends com.ulticode.modules.forum.entity.ForumComment> comments,
                                          Map<String, com.ulticode.modules.user.entity.User> authorMap);

    /**
     * Convert a ForumComment entity to ForumCommentVO.
     *
     * @param comment   the comment entity
     * @param authorMap map of author ID to User entity
     * @return the comment VO
     */
    ForumCommentVO convertToCommentVO(com.ulticode.modules.forum.entity.ForumComment comment,
                                      Map<String, com.ulticode.modules.user.entity.User> authorMap);
}
