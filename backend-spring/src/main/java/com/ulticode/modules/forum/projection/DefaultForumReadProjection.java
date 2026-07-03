package com.ulticode.modules.forum.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.ForumCommunityDetailVO;
import com.ulticode.modules.forum.dto.ForumCommunityVO;
import com.ulticode.modules.forum.dto.ForumPostThreadVO;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.dto.ForumTagVO;
import com.ulticode.modules.forum.dto.QuickFilterDTO;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import com.ulticode.modules.forum.service.ForumCommentService;
import com.ulticode.modules.forum.service.ForumPostService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link ForumReadProjection}. Owns every
 * read-side join for the forum — see the interface javadoc for why this is
 * a deep module.
 *
 * <p>The 5-post-listing reads and single-post read delegate to the existing
 * {@code ForumPostService} (which already owns the SQL + paging). The
 * community-listing / community-by-slug / tags / quick-filters reads are
 * moved here from the deleted {@code ForumService} facade. The post-thread
 * read is reassembled here (post + community + comment-tree) so callers
 * no longer bounce through three files.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultForumReadProjection implements ForumReadProjection {

    private final ForumPostService forumPostService;
    private final ForumCommentService forumCommentService;
    private final ForumCommentMapper commentMapper;
    private final ForumCommunityMapper communityMapper;
    private final ForumTagMapper tagMapper;
    private final UserService userService;

    // ---------- All posts (3 overloads) — delegate to ForumPostService ----------

    @Override
    public List<ForumPostVO> findAllPosts(String userId) {
        return forumPostService.findAllPosts(userId);
    }

    @Override
    public PageResult<ForumPostVO> findAllPosts(String userId, int page, int pageSize) {
        return forumPostService.findAllPosts(userId, page, pageSize);
    }

    @Override
    public PageResult<ForumPostVO> findAllPosts(String userId, String sortBy, int page, int pageSize) {
        return forumPostService.findAllPosts(userId, sortBy, page, pageSize);
    }

    // ---------- My posts (2 overloads) — delegate to ForumPostService ----------

    @Override
    public List<ForumPostVO> findMyPosts(String userId) {
        return forumPostService.findMyPosts(userId);
    }

    @Override
    public PageResult<ForumPostVO> findMyPosts(String userId, int page, int pageSize) {
        return forumPostService.findMyPosts(userId, page, pageSize);
    }

    // ---------- Single post — delegate to ForumPostService ----------

    @Override
    public ForumPostVO findPostById(String id, String userId) {
        return forumPostService.findPostById(id, userId);
    }

    // ---------- Thread — owns comment-tree assembly (moved from facade) ----------

    @Override
    public ForumPostThreadVO getPostThread(String postId, String userId) {
        ForumPostThreadVO thread = forumPostService.getPostThread(postId, userId);
        if (thread == null) return thread;
        List<ForumComment> comments = commentMapper.findByPostId(postId);
        Set<String> authorIds = new HashSet<>();
        comments.forEach(c -> authorIds.add(c.getAuthorId()));
        if (thread.getPost() != null && thread.getPost().getUserId() != null) {
            authorIds.add(thread.getPost().getUserId());
        }
        Map<String, User> authorMap = new HashMap<>();
        authorIds.forEach(aid -> userService.findById(aid).ifPresent(u -> authorMap.put(aid, u)));
        thread.setComments(forumCommentService.buildCommentTree(comments, authorMap));
        return thread;
    }

    // ---------- Community posts — owns sort + community map (moved from facade) ----------

    @Override
    public List<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId) {
        return findPostsByCommunity(slug, sortBy, userId, 1, 50).getItems();
    }

    @Override
    public PageResult<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId, int page, int pageSize) {
        ForumCommunity c = communityMapper.findBySlug(slug);
        if (c == null) throw new com.ulticode.common.exception.BusinessException(
                com.ulticode.common.exception.ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        int limit = Math.max(1, Math.min(pageSize, 50));
        int offset = Math.max(0, (page - 1) * limit);
        long total = forumPostService.countByCommunityId(c.getId());
        List<ForumPost> posts = forumPostService.findByCommunityId(c.getId(), limit, offset);
        Map<String, ForumCommunity> cm = Map.of(c.getId(), c);
        Map<String, User> am = forumPostService.batchLoadAuthors(posts);
        var impl = (com.ulticode.modules.forum.service.impl.ForumPostServiceImpl) forumPostService;
        Map<String, Long> commentCounts = impl.batchLoadCommentCounts(posts);
        List<ForumPostVO> items = posts.stream()
                .map(p -> impl.convertToPostVO(p, userId, am.get(p.getUserId()), cm.get(p.getCommunityId()),
                        commentCounts.getOrDefault(p.getId(), 0L)))
                .collect(Collectors.toList());
        return PageResult.of(items, total, page, limit);
    }

    // ---------- Communities + tags + filters (moved from facade) ----------

    @Override
    public List<ForumCommunityVO> findAllCommunities(boolean featuredOnly) {
        List<ForumCommunity> raw = featuredOnly
                ? communityMapper.findFeaturedCommunities()
                : communityMapper.findPublicCommunities();
        return raw.stream().map(forumPostService::toCommunityVO).collect(Collectors.toList());
    }

    @Override
    public ForumCommunityDetailVO findCommunityBySlugOrId(String slugOrId) {
        ForumCommunity c = communityMapper.findBySlug(slugOrId);
        if (c == null) c = communityMapper.selectById(slugOrId);
        if (c == null) throw new com.ulticode.common.exception.BusinessException(
                com.ulticode.common.exception.ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        ForumCommunityDetailVO d = new ForumCommunityDetailVO();
        d.setCommunity(forumPostService.toCommunityVO(c));
        d.setRules(Collections.emptyList());
        d.setLinks(Collections.emptyList());
        return d;
    }

    @Override
    public List<ForumTagVO> findAllTags() {
        return tagMapper.findAllOrderByUsage().stream()
                .map(forumPostService::toTagVO).collect(Collectors.toList());
    }

    @Override
    public List<QuickFilterDTO> getQuickFilters() {
        return List.of(
                new QuickFilterDTO("Hot", "hot"),
                new QuickFilterDTO("New", "new"),
                new QuickFilterDTO("Top", "top"));
    }
}
