package com.ulticode.modules.forum.projection;

import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.port.ForumUserReadPort;

/**
 * Forum Post projection module — single seam that owns entity-to-VO shaping
 * for {@link ForumPost}.
 *
 * <p>P7-RELOCATE-FORUM-001: {@code User} replaced with
 * {@link ForumUserReadPort.UserSummary}.
 *
 * @author ulticode
 */
public interface ForumPostProjection {

    /**
     * Batch path. Caller pre-resolves {@code community} and {@code commentCount}
     * before calling this overload, which performs no SQL.
     */
    ForumPostVO toPostVO(ForumPost post,
                         String userId,
                         ForumUserReadPort.UserSummary author,
                         ForumCommunity community,
                         long commentCount);

    /**
     * Single-item path. The projection resolves {@code community} and the
     * real comment count itself.
     */
    ForumPostVO toPostVO(ForumPost post, String userId, ForumUserReadPort.UserSummary author);
}
