package com.ulticode.modules.forum.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
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
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
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
 * injected {@link ForumPostProjection}, so the entity-to-VO shaping lives
 * in exactly one place and the write service does not duplicate it.
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
    private final UserReadProjection userReadProjection;
    private final VoteService voteService;
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
        int limit = Math.max(1, Math.min(pageSize, MAX_RECENT_POSTS));
        int safePage = Math.max(1, page);
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getIsDeleted, false);
        applySortBy(wrapper, sortBy);
        IPage<ForumPost> pageResult = postMapper.selectPage(
                new Page<>(safePage, limit), wrapper);
        return assemblePage(pageResult.getRecords(), userId, pageResult.getTotal(), safePage, limit);
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
        User author = userReadProjection.findById(post.getUserId()).orElse(null);
        return postProjection.toPostVO(post, userId, author);
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
        userReadProjection.findById(post.getUserId()).ifPresent(u -> authorMap.put(post.getUserId(), u));

        ForumPostThreadVO thread = new ForumPostThreadVO();
        thread.setPost(postProjection.toPostVO(post, userId, authorMap.get(post.getUserId()), community, 0L));

        List<ForumComment> comments = commentMapper.findByPostId(postId);
        Set<String> authorIds = new HashSet<>();
        comments.forEach(c -> authorIds.add(c.getAuthorId()));
        if (post.getUserId() != null) authorIds.add(post.getUserId());
        authorIds.forEach(aid -> userReadProjection.findById(aid).ifPresent(u -> authorMap.put(aid, u)));
        thread.setComments(commentProjection.buildCommentTree(comments, authorMap));
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
        int safePage = Math.max(1, page);
        LambdaQueryWrapper<ForumPost> wrapper = new LambdaQueryWrapper<ForumPost>()
                .eq(ForumPost::getCommunityId, c.getId())
                .eq(ForumPost::getIsDeleted, false);
        applySortBy(wrapper, sortBy);
        IPage<ForumPost> pageResult = postMapper.selectPage(
                new Page<>(safePage, limit), wrapper);
        List<ForumPost> posts = pageResult.getRecords();
        Map<String, ForumCommunity> cm = Map.of(c.getId(), c);
        Map<String, User> am = batchLoadAuthors(posts);
        Map<String, Long> commentCounts = batchLoadCommentCounts(posts);
        List<ForumPostVO> items = posts.stream()
                .map(p -> postProjection.toPostVO(p, userId,
                        am.get(p.getUserId()),
                        cm.get(p.getCommunityId()),
                        commentCounts.getOrDefault(p.getId(), 0L)))
                .collect(Collectors.toList());
        return PageResult.of(items, pageResult.getTotal(), safePage, limit);
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
                new QuickFilterDTO("hot"),
                new QuickFilterDTO("new"),
                new QuickFilterDTO("top"));
    }

    // ---------- convertToPostVO overloads — delegate to assembler (interface contract preserved) ----------

    @Override
    public ForumPostVO convertToPostVO(ForumPost post, String userId, User author) {
        return postProjection.toPostVO(post, userId, author);
    }

    @Override
    public ForumPostVO convertToPostVO(ForumPost post, String userId, User author,
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
    public Map<String, User> batchLoadAuthors(List<ForumPost> posts) {
        Set<String> ids = posts.stream().map(ForumPost::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        return userReadProjection.findAllById(ids);
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

    /**
     * Apply a sort key to the post query. The ordering chosen here is the
     * single source of truth for "hot vs top" semantics — both used to map to
     * {@code views DESC} and were indistinguishable.
     *
     * <p>Current signals (no denormalized score column exists on
     * {@code forum_posts}; live score lives in the vote edges and is merged
     * into the VO at read time, not queryable in SQL):
     * <ul>
     *   <li>{@code new} — by creation time, newest first.</li>
     *   <li>{@code hot} — recent engagement: views, then recency.</li>
     *   <li>{@code top} — all-time cumulative reach: impressions, then views.</li>
     *   <li>{@code controversial} — placeholder awaiting a vote-distribution
     *       column; currently maps to the same signal as {@code hot}. A TODO
     *       notes the future migration to surface high-vote + high-downvote
     *       posts.</li>
     * </ul>
     *
     * <p>Every branch appends {@code id DESC} as a stable tie-breaker so
     * {@code LIMIT/OFFSET} pagination does not skip or duplicate rows when
     * the primary key has duplicates (UUID v4 collisions are rare but the
     * tie-breaker also guards against seed-data id collision during tests).
     *
     * <p>Unknown values throw {@link ErrorCode#FORUM_INVALID_SORT} so a typo
     * or a frontend addition without a backend case is surfaced instead of
     * silently coerced to {@code new}.
     */
    void applySortBy(LambdaQueryWrapper<ForumPost> wrapper, String sortBy) {
        // Normalise once: callers (including hand-typed URLs and bookmarked
        // pre-i18n values) may use any case; the contract is the value, not
        // its spelling.
        String key = sortBy == null ? "" : sortBy.toLowerCase();
        if (key.isEmpty() || "new".equals(key)) {
            wrapper.orderByDesc(ForumPost::getCreatedAt);
        } else if ("hot".equals(key)) {
            wrapper.orderByDesc(ForumPost::getViews)
                    .orderByDesc(ForumPost::getCreatedAt);
        } else if ("top".equals(key)) {
            wrapper.orderByDesc(ForumPost::getImpressions)
                    .orderByDesc(ForumPost::getViews);
        } else if ("controversial".equals(key)) {
            // TODO: replace with vote-distribution ordering once a denormalized
            // score/upvotes/downvotes column is added to forum_posts (currently
            // live in vote edges, not queryable in SQL). Until then, mirror the
            // hot signal so the option is functional rather than invalid.
            wrapper.orderByDesc(ForumPost::getViews)
                    .orderByDesc(ForumPost::getCreatedAt);
        } else {
            throw new BusinessException(ErrorCode.FORUM_INVALID_SORT);
        }
        // Stable tie-breaker for every branch.
        wrapper.orderByDesc(ForumPost::getId);
    }
}