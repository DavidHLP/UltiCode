package com.ulticode.modules.forum.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.ForumCommunityDetailVO;
import com.ulticode.modules.forum.dto.ForumCommunityVO;
import com.ulticode.modules.forum.dto.ForumPostThreadVO;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.dto.ForumTagVO;
import com.ulticode.modules.forum.dto.QuickFilterDTO;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.entity.ForumTag;
import com.ulticode.modules.user.entity.User;

import java.util.List;
import java.util.Map;

/**
 * Read-side projection for the forum domain.
 *
 * <p>Extracted from the deleted {@code ForumService} facade. Owns every
 * entity-to-VO projection rule, list-query builder, batch-load helper and
 * cross-table read aggregation for the forum. After the deepening, controllers
 * depend on this projection for reads and on the write services for
 * writes; {@code ForumPostService} / {@code ForumCommentService} keep the
 * transactional write paths and inject this projection for VO building.
 *
 * <p>All methods are pure reads; single-item endpoints throw
 * {@code FORUM_POST_NOT_FOUND} or {@code FORUM_COMMUNITY_NOT_FOUND} when the
 * entity is missing or soft-deleted.
 *
 * @author ulticode
 */
public interface ForumReadProjection {

    // ---- List / single-item reads ----

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

    // ---- Entity-to-VO projection rules (formerly on ForumPostService) ----

    /**
     * Project a single post to its VO. Uses {@link #batchLoadCommentCounts}
     * for the real comment count (the {@code stats.comments} field on the
     * entity can be stale; we always re-query the count). Community is loaded
     * individually if not provided.
     */
    ForumPostVO convertToPostVO(ForumPost post, String userId, User author);

    /**
     * Project a single post to its VO with pre-loaded community + comment count.
     * Used by batch paths where community and counts are loaded once for the
     * full set of posts.
     */
    ForumPostVO convertToPostVO(ForumPost post, String userId, User author,
                                ForumCommunity community, long commentCount);

    /** Project a {@link ForumCommunity} entity to its {@link ForumCommunityVO}. */
    ForumCommunityVO toCommunityVO(ForumCommunity community);

    /** Project a {@link ForumTag} entity to its {@link ForumTagVO}. */
    ForumTagVO toTagVO(ForumTag tag);

    /**
     * Batch-load authors for a list of posts. Returns a {@code Map<userId, User>}
     * with one entry per distinct user id found in the post list.
     */
    Map<String, User> batchLoadAuthors(List<ForumPost> posts);

    /**
     * Batch-load real comment counts for a list of posts. Returns
     * {@code Map<postId, count>}. Used by the VO projection so
     * {@code stats.comments} does not leak through to the user as stale.
     */
    Map<String, Long> batchLoadCommentCounts(List<ForumPost> posts);
}
