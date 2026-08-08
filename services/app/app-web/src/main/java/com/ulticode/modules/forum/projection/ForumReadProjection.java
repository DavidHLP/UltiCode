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
import com.ulticode.modules.forum.port.ForumUserReadPort;

import java.util.List;
import java.util.Map;

/**
 * Read-side projection for the forum domain.
 *
 * <p>P7-RELOCATE-FORUM-001: {@code User} replaced with
 * {@link ForumUserReadPort.UserSummary}.
 *
 * @author ulticode
 */
public interface ForumReadProjection {

    List<ForumPostVO> findAllPosts(String userId);
    PageResult<ForumPostVO> findAllPosts(String userId, int page, int pageSize);
    PageResult<ForumPostVO> findAllPosts(String userId, String sortBy, int page, int pageSize);
    List<ForumPostVO> findMyPosts(String userId);
    PageResult<ForumPostVO> findMyPosts(String userId, int page, int pageSize);
    ForumPostVO findPostById(String id, String userId);
    ForumPostThreadVO getPostThread(String postId, String userId);
    List<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId);
    PageResult<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId, int page, int pageSize);
    List<ForumCommunityVO> findAllCommunities(boolean featuredOnly);
    ForumCommunityDetailVO findCommunityBySlugOrId(String slugOrId);
    List<ForumTagVO> findAllTags();
    List<QuickFilterDTO> getQuickFilters();

    ForumPostVO convertToPostVO(ForumPost post, String userId, ForumUserReadPort.UserSummary author);
    ForumPostVO convertToPostVO(ForumPost post, String userId, ForumUserReadPort.UserSummary author,
                                ForumCommunity community, long realCommentCount);
    ForumCommunityVO toCommunityVO(ForumCommunity c);
    ForumTagVO toTagVO(ForumTag t);
    Map<String, ForumUserReadPort.UserSummary> batchLoadAuthors(List<ForumPost> posts);
    Map<String, Long> batchLoadCommentCounts(List<ForumPost> posts);
}
