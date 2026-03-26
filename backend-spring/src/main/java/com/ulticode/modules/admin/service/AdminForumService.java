package com.ulticode.modules.admin.service;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminForumPostQueryDTO;
import com.ulticode.modules.admin.dto.AdminForumPostVO;
import com.ulticode.modules.admin.dto.BulkActionResult;

import java.util.List;

/**
 * Service interface for admin forum post management.
 */
public interface AdminForumService {

    /**
     * Get paginated list of forum posts with filters.
     *
     * @param query query parameters including filters, pagination, and sorting
     * @return paginated result of admin forum post VOs
     */
    PageResult<AdminForumPostVO> getPosts(AdminForumPostQueryDTO query);

    /**
     * Get forum post details by ID.
     *
     * @param id post ID
     * @return admin forum post VO with full details
     */
    AdminForumPostVO getPost(String id);

    /**
     * Pin a post.
     *
     * @param id post ID
     */
    void pinPost(String id);

    /**
     * Unpin a post.
     *
     * @param id post ID
     */
    void unpinPost(String id);

    /**
     * Lock a post (no comments allowed).
     *
     * @param id post ID
     */
    void lockPost(String id);

    /**
     * Unlock a post.
     *
     * @param id post ID
     */
    void unlockPost(String id);

    /**
     * Flag a post for review.
     *
     * @param id     post ID
     * @param reason reason for flagging
     */
    void flagPost(String id, String reason);

    /**
     * Unflag a post.
     *
     * @param id post ID
     */
    void unflagPost(String id);

    /**
     * Delete a post (soft delete).
     *
     * @param id post ID
     */
    void deletePost(String id);

    /**
     * Perform bulk action on multiple posts.
     *
     * @param ids    list of post IDs
     * @param action action to perform (delete, pin, unpin, lock, unlock, unflag)
     * @return bulk action result
     */
    BulkActionResult bulkAction(List<String> ids, String action);
}
