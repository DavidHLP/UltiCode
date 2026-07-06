package com.ulticode.modules.forum.projection;

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
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import com.ulticode.modules.forum.service.ForumCommentService;
import com.ulticode.modules.forum.service.ForumPostService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.service.VoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * <p>The 5-post-listing reads and single-post read delegate to the existing
 * {@code ForumPostService} (which already owns the SQL + paging). The
 * community-listing / community-by-slug / tags / quick-filters reads are
 * moved here from the deleted {@code ForumService} facade. The post-thread
 * read is reassembled here (post + community + comment-tree) so callers
 * no longer bounce through three files.
 *
 * <p><b>Seam fix:</b> the entity-to-VO projection rules ({@code convertToPostVO},
 * {@code toCommunityVO}, {@code toTagVO}) and the batch-load helpers
 * ({@code batchLoadAuthors}, {@code batchLoadCommentCounts}) used to live on
 * {@code ForumPostService} — the service interface was nearly as wide as the
 * implementation, and this projection downcasted the service to its concrete
 * impl to call methods that were not on the interface. After the deepening,
 * all of those methods live here; {@code ForumPostService} returns entities
 * or delegates to this projection for VO building. No more downcast.
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
    private final ForumCommunityMemberMapper memberMapper;
    private final ForumTagMapper tagMapper;
    private final UserService userService;
    private final VoteService voteService;

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
        if (c == null) throw new BusinessException(ErrorCode.FORUM_COMMUNITY_NOT_FOUND);
        int limit = Math.max(1, Math.min(pageSize, 50));
        int offset = Math.max(0, (page - 1) * limit);
        long total = forumPostService.countByCommunityId(c.getId());
        List<ForumPost> posts = forumPostService.findByCommunityId(c.getId(), limit, offset);
        Map<String, ForumCommunity> cm = Map.of(c.getId(), c);
        Map<String, User> am = batchLoadAuthors(posts);
        Map<String, Long> commentCounts = batchLoadCommentCounts(posts);
        List<ForumPostVO> items = posts.stream()
                .map(p -> convertToPostVO(p, userId, am.get(p.getUserId()),
                        cm.get(p.getCommunityId()),
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

    // ---------- Entity-to-VO projection rules (moved from ForumPostService) ----------

    @Override
    public ForumPostVO convertToPostVO(ForumPost post, String userId, User author) {
        // Community not provided — look up individually.
        ForumCommunity community = post.getCommunityId() != null
                ? communityMapper.selectById(post.getCommunityId())
                : null;
        long realCommentCount = post.getId() != null ? commentMapper.countByPostId(post.getId()) : 0L;
        return convertToPostVO(post, userId, author, community, realCommentCount);
    }

    @Override
    public ForumPostVO convertToPostVO(ForumPost post, String userId, User author,
                                       ForumCommunity community, long realCommentCount) {
        ForumPostVO vo = new ForumPostVO();
        vo.setId(post.getId());
        vo.setCommunityId(post.getCommunityId());
        vo.setUserId(post.getUserId());
        vo.setPermalink(post.getPermalink());
        vo.setTitle(post.getTitle());
        vo.setFlairType(post.getFlairType());
        vo.setFlairLabel(post.getFlairLabel());
        vo.setExcerpt(post.getExcerpt());
        vo.setIsSaved(post.getIsSaved());
        vo.setImpressions(post.getImpressions());
        vo.setIsPinned(post.getIsPinned());
        vo.setIsLocked(post.getIsLocked());
        vo.setViews(post.getViews());
        vo.setIsFlagged(post.getIsFlagged());
        vo.setFlaggedReason(post.getFlaggedReason());
        vo.setFlaggedAt(post.getFlaggedAt());
        vo.setCreatedAt(post.getCreatedAt());
        vo.setIsAuthor(userId != null && post.getUserId() != null && post.getUserId().equals(userId));

        // --- Tags: ensure always List<String> ---
        if (post.getTags() instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> tagList = (List<String>) post.getTags();
            vo.setTags(tagList);
        } else if (post.getTags() instanceof String) {
            try {
                @SuppressWarnings("unchecked")
                List<String> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        (String) post.getTags(), List.class);
                vo.setTags(parsed);
            } catch (Exception e) {
                log.warn("Failed to parse tags JSON for post {}: {}", post.getId(), e.getMessage());
                vo.setTags(Collections.emptyList());
            }
        } else {
            vo.setTags(Collections.emptyList());
        }

        // --- Media: ensure parsed object, not raw JSON string ---
        Object media = post.getMedia();
        if (media instanceof String) {
            try {
                media = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        (String) media, Object.class);
            } catch (Exception e) {
                log.warn("Failed to parse media JSON for post {}: {}", post.getId(), e.getMessage());
                media = null;
            }
        }
        vo.setMedia(media);

        // --- Stats: ensure always Map with likes/dislikes injected ---
        VoteResultVO vr = voteService.getVoteStatus(userId, post.getId(), EdgeOperationTargetType.FORUM_POST);
        vo.setVoteState(vr.getUserVote() == 1 ? "upvoted" : vr.getUserVote() == -1 ? "downvoted" : "neutral");

        LinkedHashMap<String, Object> statsMap = new LinkedHashMap<>();
        Object rawStats = post.getStats();
        if (rawStats instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) rawStats;
            statsMap.putAll(existing);
        } else if (rawStats instanceof String) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                        (String) rawStats, LinkedHashMap.class);
                statsMap.putAll(parsed);
            } catch (Exception e) {
                log.warn("Failed to parse stats JSON for post {}: {}", post.getId(), e.getMessage());
            }
        }
        statsMap.put("likes", vr.getLikes());
        statsMap.put("dislikes", vr.getDislikes());
        statsMap.put("score", vr.getLikes() - vr.getDislikes());
        statsMap.put("comments", realCommentCount);
        vo.setStats(statsMap);

        vo.setCommentCount(realCommentCount);

        // --- Community name/slug ---
        if (community != null) {
            vo.setCommunityName(community.getName());
            vo.setCommunitySlug(community.getSlug());
        }

        // --- Author ---
        if (author != null) {
            vo.setAuthorUsername(author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        // --- Membership ---
        if (userId != null) {
            vo.setIsMember(memberMapper.isMember(post.getCommunityId(), userId));
        }

        return vo;
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
}