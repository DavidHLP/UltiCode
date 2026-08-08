package com.ulticode.modules.admin.projection;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.AdminForumCommunityVO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.AdminForumPostQueryDTO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.AdminForumPostVO;

/**
 * Read-side projection for admin forum management &mdash; a deep module that
 * owns every entity-to-VO projection rule, paginated list-query builder and
 * community-list derivation for the admin forum surface.
 *
 * <p>This is the same shallow cluster lifted out of
 * {@link com.ulticode.modules.admin.service.AdminForumService} for the Stage 2
 * rollout of ADR-0011: the paginated post list read ({@link #getPosts}, query
 * building + batch comment/upvote/downvote/user/community enrichment + N+1-safe
 * VO shaping), the single-detail post read ({@link #getPost}), and the
 * community list derivation ({@link #getCommunities}). Sitting next to the
 * pin/unpin/lock/unlock/delete/flag/unflag write state machine in the same
 * service made every projection tweak land in the same file as the write paths.
 *
 * <p>After the deepening:
 * <ul>
 *   <li>{@link com.ulticode.modules.admin.service.AdminForumService} keeps the
 *       write state machine only (pin / unpin / lock / unlock / soft-delete /
 *       flag / unflag / bulk action) plus the audit-history delegation.
 *       Write paths never call this projection.</li>
 *   <li>The controller depends on this projection directly for reads and on
 *       the service for writes.</li>
 * </ul>
 *
 * <p>All methods are pure reads; none mutate post or community state. The
 * single-item read throws {@link com.ulticode.common.error.BaseErrorCode#NOT_FOUND}
 * to preserve the access contract observed by the controller.
 *
 * <p>Mirrors the {@link AdminSubmissionProjection} /
 * {@link com.ulticode.modules.admin.projection.AdminUserProjection} /
 * {@link com.ulticode.modules.admin.projection.AdminSolutionProjection} shape
 * exactly. Cross-module entity imports ({@code User}, {@code ForumCommunity},
 * {@code ForumComment}, {@code EdgeOperation}) live behind this seam; the admin
 * forum service no longer imports them.
 *
 * @author ulticode
 * @see AdminSubmissionProjection
 * @see com.ulticode.modules.admin.projection.AdminUserProjection
 * @see com.ulticode.modules.admin.projection.AdminSolutionProjection
 */
public interface AdminForumProjection {

    /**
     * Get a paginated list of forum posts with filters (search, community,
     * author, flagged / pinned / locked / deleted status) and sorting.
     * Cross-module enrichment (username, avatar, community name/slug, real
     * comment count, upvote / downvote counts) is batch-loaded to avoid N+1.
     *
     * @param query query parameters including filters, pagination, and sorting
     * @return paginated result of admin forum post VOs (list view shape)
     */
    PageResult<AdminForumPostVO> getPosts(AdminForumPostQueryDTO query);

    /**
     * Get a single forum post by ID with full detail fields (content).
     *
     * @param id post ID
     * @return admin forum post VO with full details
     * @throws com.ulticode.common.exception.BusinessException with
     *         {@link com.ulticode.common.error.BaseErrorCode#NOT_FOUND}
     *         when the post does not exist
     */
    AdminForumPostVO getPost(String id);

    /**
     * Get a paginated list of forum communities for the admin filter dropdown,
     * ordered by member count descending, with optional case-insensitive search
     * across name, slug and description.
     *
     * @param page  page number (1-based)
     * @param limit page size (capped at 100)
     * @param search optional case-insensitive search across name, slug and
     *               description (null or blank disables the filter)
     * @return paginated result of admin forum community VOs
     */
    PageResult<AdminForumCommunityVO> getCommunities(int page, int limit, String search);
}
