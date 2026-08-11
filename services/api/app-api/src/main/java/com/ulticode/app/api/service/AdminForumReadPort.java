package com.ulticode.app.api.service;

import com.ulticode.app.api.dto.AdminForumCommunityPage;
import com.ulticode.app.api.dto.AdminForumPostPage;
import com.ulticode.app.api.dto.AdminForumPostQuery;
import com.ulticode.app.api.dto.AdminForumPostRowDTO;

import java.util.Collection;
import java.util.Map;

/**
 * ADMIN-007: owner-only admin read surface for the {@code forum_posts} /
 * {@code forum_communities} tables in the forum module.
 *
 * <p>Consumed by the Admin service's {@code AdminForumProjection}
 * (paginated post list, single-detail read, community filter dropdown)
 * and by the Admin comment enrichment path (batch post-title lookup).
 * Returns flat typed rows — never the internal {@code ForumPost} /
 * {@code ForumCommunity} entities or mappers. Vote counts are NOT part
 * of this contract: they belong to the vote module and are read through
 * {@link ForumPostVoteCountReadPort}, composed by the Admin consumer.
 *
 * @author ulticode
 */
public interface AdminForumReadPort {

    /**
     * Paginated admin query over forum posts.
     *
     * <p>Matches the pre-migration filter semantics: {@code search}
     * matches title OR excerpt, all optional state filters are exact
     * matches, and the default sort is {@code createdAt} desc. The
     * {@code commentCount} sort is evaluated by App-owned SQL before
     * pagination over non-deleted comments, with created_at/id tie-breaks;
     * consumers preserve the returned row order.
     *
     * @param query filters, sort and pagination
     * @return matching rows (with community name/slug and comment counts
     *         populated; vote counts left {@code null}) plus the total
     */
    AdminForumPostPage listPosts(AdminForumPostQuery query);

    /**
     * Single forum post row by id.
     *
     * @param postId post ID
     * @return the row with {@code content} populated (detail view) and
     *         comment count set; vote counts left {@code null}; or
     *         {@code null} when the post does not exist
     */
    AdminForumPostRowDTO getPost(String postId);

    /**
     * Paginated community list for the admin filter dropdown.
     *
     * @param page   1-based page number
     * @param limit  page size
     * @param search optional case-insensitive match across name, slug and
     *               description
     * @return communities ordered by member count desc, plus the total
     */
    AdminForumCommunityPage listCommunities(int page, int limit, String search);

    /**
     * Batch-load forum-post titles for the given post ids.
     *
     * <p>Consumed by the Admin comment enrichment
     * ({@code AdminCommentReadAdapter}) which previously imported
     * {@code ForumPostMapper} directly. Batch {@code selectBatchIds}
     * semantics: logical-deleted rows are excluded.
     *
     * @param postIds candidate post IDs
     * @return map keyed by post id; ids with no matching post are absent
     *         from the map. Empty input returns an empty map.
     */
    Map<String, String> findPostTitlesByIds(Collection<String> postIds);
}
