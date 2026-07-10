package com.ulticode.modules.forum.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.forum.dto.ForumCommunityDetailVO;
import com.ulticode.modules.forum.dto.ForumCommunityVO;
import com.ulticode.modules.forum.dto.ForumPostThreadVO;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.dto.ForumTagVO;
import com.ulticode.modules.forum.dto.ForumPostVOAssembler;
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
import com.ulticode.modules.forum.service.ForumCommentService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.modules.vote.service.VoteService;
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
 * static {@link ForumPostVOAssembler}, so projection and write service
 * stay decoupled.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultForumReadProjection implements ForumReadProjection {

    private static final int MAX_RECENT_POSTS = 50;

    private final ForumPostMapper postMapper;
    private final ForumCommentService forumCommentService;
    private final ForumCommentMapper commentMapper;
    private final ForumCommunityMapper communityMapper;
    private final ForumCommunityMemberMapper memberMapper;
    private final ForumTagMapper tagMapper;
    private final UserService userService;
    private final VoteService voteService;

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
        int limit = Math.max(1, Math.min(pageSize, MAX_RECENT_POSTS));
        int offset = Math.max(0, (page - 1) * limit);
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getIsDeleted, false);
        applySortBy(wrapper, sortBy);
        long total = postMapper.selectCount(
                new LambdaQueryWrapper<ForumPost>().eq(ForumPost::getIsDeleted, false));
        List<ForumPost> posts = postMapper.selectList(wrapper.last("LIMIT " + limit + " OFFSET " + offset));
        return assemblePage(posts, userId, total, page, limit);
    }

    // ---------- My posts (2 overloads) ----------

    @Override
    public List<ForumPostVO> findMyPosts(String userId) {
        return findMyPosts(userId, 1, MAX_RECENT_POSTS).getItems();
    }

    @Override
    public PageResult<ForumPostVO> findMyPosts(String userId, int page, int pageSize) {
        int limit = Math.max(1, Math.min(pageSize, MAX_RECENT_POSTS));
        int offset = Math.max(0, (page - 1) * limit);
        long total = postMapper.countByUserId(userId);
        List<ForumPost> posts = postMapper.findByUserId(userId);
        // Manual pagination since findByUserId returns full list
        List<ForumPost> paged = posts.stream().skip(offset).limit(limit).collect(Collectors.toList());
        return assemblePage(paged, userId, total, page, limit);
    }

    // ---------- Single post ----------

    @Override
    public ForumPostVO findPostById(String id, String userId) {
        ForumPost post = postMapper.selectById(id);
        if (post == null) throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        User author = userService.findById(post.getUserId()).orElse(null);
        return ForumPostVOAssembler.toPostVO(post, userId, author,
                voteService, communityMapper, commentMapper, memberMapper);
    }

    // ---------- Thread — owns full assembly (was previously split with service stub) ----------

    @Override
    public ForumPostThreadVO getPostThread(String postId, String userId) {
        ForumPost post = postMapper.selectById(postId);
        if (post == null) throw new BusinessException(ErrorCode.FORUM_POST_NOT_FOUND);
        ForumCommunity community = post.getCommunityId() != null
                ? communityMapper.selectById(post.getCommunityId())
                : null;
        Map<String, User> authorMap = new HashMap<>();
        userService.findById(post.getUserId()).ifPresent(u -> authorMap.put(post.getUserId(), u));

        ForumPostThreadVO thread = new ForumPostThreadVO();
        thread.setPost(ForumPostVOAssembler.toPostVO(post, userId,
                authorMap.get(post.getUserId()), community, 0L,
                voteService, memberMapper));

        List<ForumComment> comments = commentMapper.findByPostId(postId);
        Set<String> authorIds = new HashSet<>();
        comments.forEach(c -> authorIds.add(c.getAuthorId()));
        if (post.getUserId() != null) authorIds.add(post.getUserId());
        authorIds.forEach(aid -> userService.findById(aid).ifPresent(u -> authorMap.put(aid, u)));
        thread.setComments(forumCommentService.buildCommentTree(comments, authorMap));
        return thread;
    }

    // ---------- Community posts — owns sort + community map ----------

    @Override
    public List<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId) {
        return findPostsByCommunity(slug, sortBy, userId, 1, 50).getItems();
    }

    @Override
    public PageResult<ForumPostVO> findPostsByCommunity(String slug, String sortBy, String userId, int page, int pageSize) {
        ForumCommunity c = communityMapper.findBySlug(slug);
        if (c == null) throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        int limit = Math.max(1, Math.min(pageSize, 50));
        int offset = Math.max(0, (page - 1) * limit);
        long total = postMapper.countByCommunityId(c.getId());
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getCommunityId, c.getId())
                .eq(ForumPost::getIsDeleted, false)
                .orderByDesc(ForumPost::getCreatedAt)
                .last("LIMIT " + limit + " OFFSET " + offset);
        List<ForumPost> posts = postMapper.selectList(wrapper);
        Map<String, ForumCommunity> cm = Map.of(c.getId(), c);
        Map<String, User> am = batchLoadAuthors(posts);
        Map<String, Long> commentCounts = batchLoadCommentCounts(posts);
        List<ForumPostVO> items = posts.stream()
                .map(p -> ForumPostVOAssembler.toPostVO(p, userId,
                        am.get(p.getUserId()),
                        cm.get(p.getCommunityId()),
                        commentCounts.getOrDefault(p.getId(), 0L),
                        voteService, memberMapper))
                .collect(Collectors.toList());
        return PageResult.of(items, total, page, limit);
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
        if (c == null) throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
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
                new QuickFilterDTO("Hot", "hot"),
                new QuickFilterDTO("New", "new"),
                new QuickFilterDTO("Top", "top"));
    }

    // ---------- convertToPostVO overloads — delegate to assembler (interface contract preserved) ----------

    @Override
    public ForumPostVO convertToPostVO(ForumPost post, String userId, User author) {
        return ForumPostVOAssembler.toPostVO(post, userId, author,
                voteService, communityMapper, commentMapper, memberMapper);
    }

    @Override
    public ForumPostVO convertToPostVO(ForumPost post, String userId, User author,
                                       ForumCommunity community, long realCommentCount) {
        return ForumPostVOAssembler.toPostVO(post, userId, author,
                community, realCommentCount, voteService, memberMapper);
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
    public Map<String, User> batchLoadAuthors(List<ForumPost> posts) {
        Set<String> ids = posts.stream().map(ForumPost::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        return userService.findAllById(ids);
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

    /**
     * Batch assemble a page of posts: authors + communities + comment counts are
     * pre-loaded once and reused per post (avoids N+1 queries).
     */
    private PageResult<ForumPostVO> assemblePage(List<ForumPost> posts, String userId,
                                                 long total, int page, int limit) {
        Map<String, User> authorMap = batchLoadAuthors(posts);
        Map<String, ForumCommunity> communityMap = batchLoadCommunities(posts);
        Map<String, Long> commentCounts = batchLoadCommentCounts(posts);
        List<ForumPostVO> items = posts.stream()
                .map(p -> ForumPostVOAssembler.toPostVO(
                        p, userId,
                        authorMap.get(p.getUserId()),
                        communityMap.get(p.getCommunityId()),
                        commentCounts.getOrDefault(p.getId(), 0L),
                        voteService, memberMapper))
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
        if (sortBy == null || sortBy.isEmpty() || "new".equals(sortBy)) {
            wrapper.orderByDesc(ForumPost::getCreatedAt);
        } else if ("hot".equals(sortBy)) {
            wrapper.orderByDesc(ForumPost::getViews);
        } else if ("top".equals(sortBy)) {
            wrapper.orderByDesc(ForumPost::getViews);
        } else {
            wrapper.orderByDesc(ForumPost::getCreatedAt);
        }
    }
}