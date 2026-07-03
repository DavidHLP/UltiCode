package com.ulticode.modules.forum.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.ForumCommunityDetailVO;
import com.ulticode.modules.forum.dto.ForumCommunityVO;
import com.ulticode.modules.forum.dto.ForumPostThreadVO;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.dto.ForumTagVO;
import com.ulticode.modules.forum.dto.QuickFilterDTO;

import java.util.List;

/**
 * Read-side projection for the forum domain.
 *
 * <p>Extracted from the deleted {@code ForumService} facade. Owns every
 * entity-to-VO projection rule, list-query builder and cross-table read
 * aggregation for the forum. After the deepening, controllers depend on
 * this projection for reads and on {@code ForumWritePort} for writes;
 * {@code ForumPostService} / {@code ForumCommentService} keep the
 * transactional write paths and the {@code convertToPostVO} helper.
 *
 * <p>All methods are pure reads; single-item endpoints throw
 * {@code FORUM_POST_NOT_FOUND} or {@code FORUM_COMMUNITY_NOT_FOUND} when the
 * entity is missing or soft-deleted.
 *
 * @author ulticode
 */
public interface ForumReadProjection {

    /** Most recent posts (default sort = new, max 50). */
    List<ForumPostVO> findAllPosts(String userId);

    /** Posts with default sort, explicit pagination. */
    PageResult<ForumPostVO> findAllPosts(String userId, int page, int pageSize);

    /** Posts with explicit sort + pagination. */
    PageResult<ForumPostVO> findAllPosts(String userId, String sortBy, int page, int pageSize);

    /** Posts authored by {@code userId} (default sort = new, max 50). */
    List<ForumPostVO> findMyPosts(String userId);

    /** Posts authored by {@code userId}, paginated. */
    PageResult<ForumPostVO> findMyPosts(String userId, int page, int pageSize);

    /** Single post by id (404 when missing). */
    ForumPostVO findPostById(String id, String userId);

    /** Post with full comment tree (top-level + replies). */
    ForumPostThreadVO getPostThread(String postId, String userId);

    /** Posts for a community, default sort = new, max 50. */
    List<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId);

    /** Posts for a community, paginated. */
    PageResult<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId, int page, int pageSize);

    /** All communities (or featured only). */
    List<ForumCommunityVO> findAllCommunities(boolean featuredOnly);

    /** Community by slug or id (404 when neither resolves). */
    ForumCommunityDetailVO findCommunityBySlugOrId(String slugOrId);

    /** All tags, ordered by usage. */
    List<ForumTagVO> findAllTags();

    /** Static quick-filter list. */
    List<QuickFilterDTO> getQuickFilters();
}
