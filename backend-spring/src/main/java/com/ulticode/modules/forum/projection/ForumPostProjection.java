package com.ulticode.modules.forum.projection;

import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.user.entity.User;

/**
 * Forum Post projection module — single seam that owns entity-to-VO shaping
 * for {@link ForumPost}.
 *
 * <p>Extracted from the previous static {@code ForumPostVOAssembler} so that
 * every read and write caller depends on one injected contract instead of a
 * static utility with two long parameter lists (one for the batch path, one
 * for the single-item path). Architecture-review candidate #2.
 *
 * <p>The implementation ({@link DefaultForumPostProjection}) is a Spring
 * service that owns:
 * <ul>
 *   <li>JSON normalisation of {@code tags}, {@code media}, and {@code stats}
 *       (string-encoded or already-typed inputs are both handled);</li>
 *   <li>vote-state enrichment via {@code VoteService};</li>
 *   <li>membership lookup via {@code ForumCommunityMemberMapper};</li>
 *   <li>community + comment-count resolution for the single-item path;</li>
 *   <li>author enrichment.</li>
 * </ul>
 *
 * <p>Callers that already pre-resolve community + comment count (batch paths)
 * use the four-argument overload; the two-argument overload exists for
 * write paths and the single-post read.
 *
 * @author ulticode
 */
public interface ForumPostProjection {

    /**
     * Batch path. Caller pre-resolves {@code community} and {@code commentCount}
     * (e.g. via {@code ForumReadProjection#batchLoadCommentCounts}). This
     * overload performs no SQL.
     */
    ForumPostVO toPostVO(ForumPost post,
                         String userId,
                         User author,
                         ForumCommunity community,
                         long commentCount);

    /**
     * Single-item path. The projection resolves {@code community} and the
     * real comment count itself. Used by write paths and the single-post
     * read where the caller has not pre-batched.
     */
    ForumPostVO toPostVO(ForumPost post, String userId, User author);
}
