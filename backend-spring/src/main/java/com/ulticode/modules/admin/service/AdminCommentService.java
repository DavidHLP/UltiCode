package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.dto.BulkCommentActionRequest;

/**
 * Service interface for admin comment management.
 */
public interface AdminCommentService {

    /**
     * Get paginated list of comments with filters.
     *
     * @param query query parameters including filters, pagination, and sorting
     * @return paginated result of admin comment VOs
     */
    PageResult<AdminCommentVO> getComments(AdminCommentQueryDTO query);

    /**
     * Get comment details by ID and type.
     *
     * @param id   comment ID
     * @param type comment type ("forum" or "solution")
     * @return admin comment VO with full details
     */
    AdminCommentVO getComment(String id, String type);

    /**
     * Flag a comment for review.
     *
     * @param id     comment ID
     * @param type   comment type ("forum" or "solution")
     * @param reason reason for flagging
     */
    void flagComment(String id, String type, String reason);

    /**
     * Unflag a comment.
     *
     * @param id   comment ID
     * @param type comment type ("forum" or "solution")
     */
    void unflagComment(String id, String type);

    /**
     * Delete a comment (soft delete).
     *
     * @param id   comment ID
     * @param type comment type ("forum" or "solution")
     */
    void deleteComment(String id, String type);

    /**
     * Perform bulk action on multiple comments.
     *
     * @param request bulk action request with IDs, type, and action
     * @return bulk action result
     */
    BulkActionResult bulkCommentAction(BulkCommentActionRequest request);
}
