package com.ulticode.modules.admin.service.comment;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;

/**
 * Polymorphic seam for admin comment moderation across heterogeneous comment
 * stores. Each implementation owns one comment-bearing aggregate
 * (forum / solution) and absorbs the {@code if ("forum".equals(type)) ... else
 * if ("solution".equals(type)) ...} branch that previously lived in
 * {@code AdminCommentServiceImpl}.
 *
 * <p>The seam narrows the five moderated operations (list / get / flag /
 * unflag / delete) to a typed contract so the service layer can stay a thin
 * router keyed by {@link #getType()}. Adding a third comment store later
 * (e.g. contest comments) is a one-bean registration — no service edit.
 *
 * <p>Each moderator owns its own mapper, audit context writes (the
 * {@code type} field in the audit diff varies per moderator), and
 * enrichment against {@link com.ulticode.modules.admin.port.AdminCommentReadPort}.
 * Moderators are {@code @Component}s (not {@code @Service}s) to make the
 * seam structurally distinct from the service layer.
 *
 * @author ulticode
 */
public interface CommentModerator {

    /**
     * The discriminator this moderator serves.
     *
     * @return the type tag used by the API and the audit diff — one of
     *         {@code "forum"} or {@code "solution"}
     */
    String getType();

    /**
     * List comments with the supplied filter query and pagination.
     *
     * @param query the admin-side filter DTO; the moderator ignores its
     *              {@code type} field (it is the moderator's own discriminator)
     * @param page  the 1-based page number
     * @param limit the page size
     * @return a {@link PageResult} of {@link AdminCommentVO}
     */
    PageResult<AdminCommentVO> listComments(AdminCommentQueryDTO query, int page, int limit);

    /**
     * Look up a single comment by id.
     *
     * @param commentId the comment id
     * @return the enriched {@link AdminCommentVO}
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.error.BaseErrorCode#NOT_FOUND} when
     *         the comment does not exist
     */
    AdminCommentVO getComment(String commentId);

    /**
     * Flag a comment for review.
     *
     * @param commentId the comment id
     * @param reason    the flag reason (may be {@code null})
     */
    void flagComment(String commentId, String reason);

    /**
     * Clear the flag on a comment.
     *
     * @param commentId the comment id
     */
    void unflagComment(String commentId);

    /**
     * Soft-delete a comment. The deletion is reversible; only the
     * {@code is_deleted}, {@code deleted_at}, {@code deleted_by} columns are
     * touched.
     *
     * @param commentId the comment id
     */
    void deleteComment(String commentId);
}
