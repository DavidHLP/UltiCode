package com.ulticode.modules.forum.projection;

import com.ulticode.modules.forum.dto.ForumCommentVO;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.port.ForumUserReadPort;

import java.util.List;
import java.util.Map;

/**
 * Forum comment projection module — the single seam that owns entity-to-VO
 * shaping and comment-tree assembly for {@link ForumComment}.
 *
 * <p>P7-RELOCATE-FORUM-001: {@code User} replaced with
 * {@link ForumUserReadPort.UserSummary}.
 *
 * @author ulticode
 */
public interface ForumCommentProjection {

    /**
     * Convert a single {@link ForumComment} entity to its VO, enriching author
     * fields from {@code authorMap} when the author is present.
     */
    ForumCommentVO toCommentVO(ForumComment comment, Map<String, ForumUserReadPort.UserSummary> authorMap);

    /**
     * Build a hierarchical list of comment VOs (nested replies) from a flat
     * list of comment entities.
     */
    List<ForumCommentVO> buildCommentTree(List<? extends ForumComment> comments,
                                          Map<String, ForumUserReadPort.UserSummary> authorMap);
}
