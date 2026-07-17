package com.ulticode.modules.forum.projection;

import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.user.entity.User;

import java.util.List;
import java.util.Map;

/**
 * Forum comment projection module — the single seam that owns entity-to-VO
 * shaping and comment-tree assembly for {@link ForumComment}.
 *
 * <p>Mirrors {@link ForumPostProjection}: extracted so both the read side
 * ({@link ForumReadProjection#getPostThread}) and the write side
 * ({@code ForumCommentService#createComment}/{@code updateComment}) depend on
 * one injected projection contract instead of the write service exposing
 * read-shaping methods on its own interface. Moving the tree shape here also
 * removes the {@code DefaultForumReadProjection -> ForumCommentService} cycle.
 *
 * @author ulticode
 */
public interface ForumCommentProjection {

    /**
     * Convert a single {@link ForumComment} entity to its VO, enriching author
     * fields from {@code authorMap} when the author is present.
     *
     * @param comment   the comment entity
     * @param authorMap map of author ID to {@link User} entity
     * @return the comment VO (author fields absent when the author is unknown)
     */
    ForumCommentVO toCommentVO(ForumComment comment, Map<String, User> authorMap);

    /**
     * Build a hierarchical list of comment VOs (nested replies) from a flat
     * list of comment entities.
     *
     * @param comments  the flat list of comments
     * @param authorMap map of author ID to {@link User} entity
     * @return top-level comment VOs with nested {@code replies}
     */
    List<ForumCommentVO> buildCommentTree(List<? extends ForumComment> comments,
                                          Map<String, User> authorMap);
}
