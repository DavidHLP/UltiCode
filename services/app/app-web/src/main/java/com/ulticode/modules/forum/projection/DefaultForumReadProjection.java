package com.ulticode.modules.forum.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
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
import com.ulticode.modules.forum.entity.ForumTag;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import com.ulticode.modules.forum.port.ForumUserReadPort;
import com.ulticode.app.error.ForumErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link ForumReadProjection}. Owns every
 * read-side join for the forum — see the interface javadoc for why this is
 * a deep module.
 *
 * <p>After the cycle-breaking refactor this class owns all read-side SQL +
 * paging + VO assembly. It does not depend on {@code ForumPostService}.
 * Write-side code (createPost / updatePost) returns VOs by calling the
 * injected {@link ForumPostProjection}, so the entity-to-VO shaping lives
 * in exactly one place and the write service does not duplicate it.
 *
 * <p>P7-RELOCATE-FORUM-001: {@code User} replaced with
 * {@link ForumUserReadPort.UserSummary}; dead {@code VoteService} field
 * removed (vote state is enriched in {@link ForumPostProjection} via
 * {@link com.ulticode.app.api.service.ForumVoteReadPort}).
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultForumReadProjection implements ForumReadProjection {

    private static final int MAX_RECENT_POSTS = 50;

    private final ForumPostMapper postMapper;
    private final ForumCommentProjection commentProjection;
    private final ForumCommentMapper commentMapper;
    private final ForumCommunityMapper communityMapper;
    private final ForumCommunityMemberMapper memberMapper;
    private final ForumTagMapper tagMapper;
    private final ForumUserReadPort forumUserReadPort;
    private final ForumPostProjection postProjection;

    // ---------- All posts (3 overloads) ----------

    @Override
    public List<ForumPostVO> findAllPosts(String userId) {
        return findAllPosts(userId, "new", 1, MAX_RECENT_POSTS).getItems();
    }

    @Override
    public PageResult<ForumPostVO> findAllPosts(String userId, int page, int pageSize) {
        return findAllPosts(userId, "new", page, pageSize);
    }

    @Override
    public PageResult<ForumPostVO> findAllPosts(String userId, String sortBy, int page, int pageSize) {
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getIsDeleted, false);
        applySortBy(wrapper, sortBy);
        IPage<ForumPost> pageResult = paginate(wrapper, page, pageSize, MAX_RECENT_POSTS);
        return assemblePage(pageResult.getRecords(), userId, pageResult.getTotal(),
                (int) pageResult.getCurrent(), (int) pageResult.getSize());
    }

    // ---------- My posts (2 overloads) ----------

    @Override
    public List<ForumPostVO> findMyPosts(String userId) {
        return findMyPosts(userId, 1, MAX_RECENT_POSTS).getItems();
    }

    @Override
    public PageResult<ForumPostVO> findMyPosts(String userId, int page, int pageSize) {
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getUserId, userId)
                .eq(ForumPost::getIsDeleted, false)
                .orderByDesc(ForumPost::getCreatedAt)
                .orderByDesc(ForumPost::getId);
        IPage<ForumPost> pageResult = paginate(wrapper, page, pageSize, MAX_RECENT_POSTS);
        return assemblePage(pageResult.getRecords(), userId, pageResult.getTotal(),
                (int) pageResult.getCurrent(), (int) pageResult.getSize());
    }

    // ---------- Single post ----------

    @Override
    public ForumPostVO findPostById(String id, String userId) {
        ForumPost post = postMapper.selectById(id);
        if (post == null) throw new BusinessException(ForumErrorCode.FORUM_POST_NOT_FOUND);
        ForumUserReadPort.UserSummary author = forumUserReadPort.findById(post.getUserId());
        return postProjection.toPostVO(post, userId, author);
    }

    // ---------- Thread — owns full assembly ----------

    @Override
    public ForumPostThreadVO getPostThread(String postId, String userId) {
        ForumPost post = postMapper.selectById(postId);
        if (post == null) throw new BusinessException(ForumErrorCode.FORUM_POST_NOT_FOUND);
        ForumCommunity community = post.getCommunityId() != null
                ? communityMapper.selectById(post.getCommunityId())
                : null;
        Map<String, ForumUserReadPort.UserSummary> authorMap = new HashMap<>();
        ForumUserReadPort.UserSummary postAuthor = forumUserReadPort.findById(post.getUserId());
        if (postAuthor != null) authorMap.put(post.getUserId(), postAuthor);

        ForumPostThreadVO thread = new ForumPostThreadVO();
        thread.setPost(postProjection.toPostVO(post, userId, postAuthor, community, 0L));

        List<ForumComment> comments = commentMapper.findByPostId(postId);
        Set<String> authorIds = new HashSet<>();
        comments.forEach(c -> authorIds.add(c.getAuthorId()));
        if (post.getUserId() != null) authorIds.add(post.getUserId());
        authorMap.putAll(forumUserReadPort.findAllById(authorIds));
        thread.setComments(commentProjection.buildCommentTree(comments, authorMap));
        return thread;
    }

    // ---------- Community posts ----------

    @Override
    public List<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId) {
        return findPostsByCommunity(slug, sortBy, userId, 1, 50).getItems();
    }

    @Override
    public PageResult<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId, int page, int pageSize) {
        ForumCommunity c = communityMapper.findBySlug(slug);
        if (c == null) throw new BusinessException(ForumErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getCommunityId, c.getId())
                .eq(ForumPost::getIsDeleted, false);
        applySortBy(wrapper, sortBy);
        IPage<ForumPost> pageResult = paginate(wrapper, page, pageSize, 50);
        List<ForumPost> posts = pageResult.getRecords();
        Map<String, ForumCommunity> cm = Map.of(c.getId(), c);
        Map<String, ForumUserReadPort.UserSummary> am = batchLoadAuthors(posts);
        Map<String, Long> commentCounts = batchLoadCommentCounts(posts);
        List<ForumPostVO> items = posts.stream()
                .map(p -> postProjection.toPostVO(p, userId,
                        am.get(p.getUserId()),
                        cm.get(p.getCommunityId()),
                        commentCounts.getOrDefault(p.getId(), 0L)))
                .collect(Collectors.toList());
        return PageResult.of(items, pageResult.getTotal(),
                (int) pageResult.getCurrent(), (int) pageResult.getSize());
    }

    // ---------- Communities + tags + filters ----------

    @Override
    public List<ForumCommunityVO> findAllCommunities(boolean featuredOnly) {
        List<ForumCommunity> raw = featuredOnly
                ? communityMapper.findFeaturedCommunities()
                : communityMapper.findPublicCommunities();
        return raw.stream().map(this::toCommunityVO).collect(Collectors.toList());
    }

    @Override
    public ForumCommunityDetailVO findCommunityBySlugOrId(String slugOrId) {
        ForumCommunity c = communityMapper.findBySlug(slugOrId);
        if (c == null) c = communityMapper.selectById(slugOrId);
        if (c == null) throw new BusinessException(ForumErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        ForumCommunityDetailVO d = new ForumCommunityDetailVO();
        d.setCommunity(toCommunityVO(c));
        d.setRules(Collections.emptyList());
        d.setLinks(Collections.emptyList());
        return d;
    }

    @Override
    public List<ForumTagVO> findAllTags() {
        return tagMapper.findAllOrderByUsage().stream()
                .map(this::toTagVO).collect(Collectors.toList());
    }

    @Override
    public List<QuickFilterDTO> getQuickFilters() {
        return List.of(
                new QuickFilterDTO("hot"),
                new QuickFilterDTO("new"),
                new QuickFilterDTO("top"),
                new QuickFilterDTO("controversial"));
    }

    // ---------- Conversion helpers ----------

    @Override
    public ForumPostVO convertToPostVO(ForumPost post, String userId, ForumUserReadPort.UserSummary author) {
        return postProjection.toPostVO(post, userId, author);
    }

    @Override
    public ForumPostVO convertToPostVO(ForumPost post, String userId, ForumUserReadPort.UserSummary author,
                                       ForumCommunity community, long realCommentCount) {
        return postProjection.toPostVO(post, userId, author, community, realCommentCount);
    }

    @Override
    public ForumCommunityVO toCommunityVO(ForumCommunity c) {
        ForumCommunityVO v = new ForumCommunityVO();
        v.setId(c.getId());
        v.setName(c.getName());
        v.setSlug(c.getSlug());
        v.setDescription(c.getDescription());
        v.setMembers(c.getMembers());
        v.setOnline(c.getOnline());
        v.setIcon(c.getIcon());
        v.setColor(c.getColor());
        v.setBanner(c.getBanner());
        v.setPostsCount(c.getPostsCount());
        v.setPostsToday(c.getPostsToday());
        v.setPostsWeek(c.getPostsWeek());
        v.setIsOfficial(c.getIsOfficial());
        v.setIsFeatured(c.getIsFeatured());
        v.setSortOrder(c.getSortOrder());
        v.setCreatedAt(c.getCreatedAt());
        v.setVisibility(c.getVisibility());
        return v;
    }

    @Override
    public ForumTagVO toTagVO(ForumTag t) {
        ForumTagVO v = new ForumTagVO();
        v.setId(t.getId());
        v.setName(t.getName());
        v.setSlug(t.getSlug());
        v.setDescription(t.getDescription());
        v.setColor(t.getColor());
        v.setUsageCount(t.getUsageCount());
        v.setCreatedAt(t.getCreatedAt());
        return v;
    }

    @Override
    public Map<String, ForumUserReadPort.UserSummary> batchLoadAuthors(List<ForumPost> posts) {
        Set<String> ids = posts.stream().map(ForumPost::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        return forumUserReadPort.findAllById(ids);
    }

    @Override
    public Map<String, Long> batchLoadCommentCounts(List<ForumPost> posts) {
        List<String> ids = posts.stream()
                .map(ForumPost::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        return commentMapper.countByPostIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> String.valueOf(row.getOrDefault("post_id", row.get("postId"))),
                        row -> ((Number) row.getOrDefault("cnt", row.get("count"))).longValue()));
    }

    // ---------- Private helpers ----------

    private IPage<ForumPost> paginate(LambdaQueryWrapper<ForumPost> wrapper,
                                      int page, int pageSize, int maxLimit) {
        int limit = Math.max(1, Math.min(pageSize, maxLimit));
        int safePage = Math.max(1, page);
        return postMapper.selectPage(new Page<>(safePage, limit), wrapper);
    }

    private PageResult<ForumPostVO> assemblePage(List<ForumPost> posts, String userId,
                                                 long total, int page, int limit) {
        Map<String, ForumUserReadPort.UserSummary> authorMap = batchLoadAuthors(posts);
        Map<String, ForumCommunity> communityMap = batchLoadCommunities(posts);
        Map<String, Long> commentCounts = batchLoadCommentCounts(posts);
        List<ForumPostVO> items = posts.stream()
                .map(p -> postProjection.toPostVO(
                        p, userId,
                        authorMap.get(p.getUserId()),
                        communityMap.get(p.getCommunityId()),
                        commentCounts.getOrDefault(p.getId(), 0L)))
                .collect(Collectors.toList());
        return PageResult.of(items, total, page, limit);
    }

    private Map<String, ForumCommunity> batchLoadCommunities(List<ForumPost> posts) {
        Set<String> ids = posts.stream().map(ForumPost::getCommunityId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        return ids.stream()
                .map(communityMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ForumCommunity::getId, fc -> fc));
    }

    private void applySortBy(LambdaQueryWrapper<ForumPost> wrapper, String sortBy) {
        String normalisedSortBy = sortBy == null ? "" : sortBy.toLowerCase();
        if (normalisedSortBy.isEmpty() || "new".equals(normalisedSortBy)) {
            wrapper.orderByDesc(ForumPost::getCreatedAt);
        } else if ("hot".equals(normalisedSortBy)) {
            wrapper.orderByDesc(ForumPost::getViews)
                    .orderByDesc(ForumPost::getCreatedAt);
        } else if ("top".equals(normalisedSortBy)) {
            wrapper.orderByDesc(ForumPost::getImpressions)
                    .orderByDesc(ForumPost::getViews);
        } else if ("controversial".equals(normalisedSortBy)) {
            wrapper.orderByDesc(ForumPost::getViews)
                    .orderByDesc(ForumPost::getCreatedAt);
        } else if ("explore".equals(normalisedSortBy)) {
            wrapper.orderByDesc(ForumPost::getCreatedAt);
        } else {
            throw new BusinessException(ForumErrorCode.FORUM_INVALID_SORT);
        }
        wrapper.orderByDesc(ForumPost::getId);
    }
}
