package com.ulticode.modules.forum.service;

import com.ulticode.modules.forum.dto.CreateCommentDTO;
import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.dto.UpdateCommentDTO;

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
}
