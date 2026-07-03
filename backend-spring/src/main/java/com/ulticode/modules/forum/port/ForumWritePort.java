package com.ulticode.modules.forum.port;

import com.ulticode.modules.forum.dto.CreateCommentDTO;
import com.ulticode.modules.forum.dto.CreatePostDTO;
import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.dto.UpdateCommentDTO;
import com.ulticode.modules.forum.dto.UpdatePostDTO;

/**
 * Write surface for the forum domain.
 *
 * <p>Extracted from the deleted {@code ForumService} facade. Owns every
 * mutating operation on forum posts, comments and community membership
 * behind a small interface. The default adapter delegates to
 * {@code ForumPostService} / {@code ForumCommentService} so the existing
 * {@code @Transactional} / {@code @CheckBan} guards are preserved. The
 * dependency category is in-process; the seam is real because controllers
 * are the only callers and the default adapter is the only provider today
 * (tests can substitute a fake).
 *
 * @author ulticode
 */
public interface ForumWritePort {

    /** Create a new post in a community. */
    ForumPostVO createPost(CreatePostDTO dto, String userId);

    /** Update an existing post (403 not author, 423 locked). */
    ForumPostVO updatePost(String id, UpdatePostDTO dto, String userId);

    /** Soft-delete a post (403 not author). */
    void deletePost(String id, String userId);

    /** Increment impressions counter. */
    void recordShare(String postId);

    /** Increment views counter. */
    void recordView(String postId);

    /** Create a comment on a post (404 missing, 423 locked). */
    ForumCommentVO createComment(String postId, CreateCommentDTO dto, String userId);

    /** Update a comment (403 not author). */
    ForumCommentVO updateComment(String id, UpdateCommentDTO dto, String userId);

    /** Soft-delete a comment (403 not author). */
    void deleteComment(String id, String userId);

    /** Join a community (idempotent; 404 missing). */
    void joinCommunity(String communityId, String userId);

    /** Leave a community. */
    void leaveCommunity(String communityId, String userId);
}
