package com.ulticode.modules.forum.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.vote.dto.VoteResultVO;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.service.VoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link ForumPostProjection} implementation.
 *
 * <p>Owns every collaborator the projection needs (mappers, vote service,
 * ObjectMapper for JSON normalisation) so callers no longer thread four to
 * six parameters through. Previously the same logic lived in
 * {@code ForumPostVOAssembler} as static methods.
 *
 * <p>Behaviour is identical to the previous static assembler — every field
 * mapping, JSON-parse fallback, vote-state derivation, and stats merge is
 * preserved verbatim. Architecture-review candidate #2.
 *
 * @author ulticode
 */
@Slf4j
@Component
public class DefaultForumPostProjection implements ForumPostProjection {

    private final VoteService voteService;
    private final ForumCommunityMemberMapper memberMapper;
    private final ForumCommunityMapper communityMapper;
    private final ForumCommentMapper commentMapper;
    private final ObjectMapper objectMapper;

    public DefaultForumPostProjection(VoteService voteService,
                                     ForumCommunityMemberMapper memberMapper,
                                     ForumCommunityMapper communityMapper,
                                     ForumCommentMapper commentMapper,
                                     ObjectMapper objectMapper) {
        this.voteService = voteService;
        this.memberMapper = memberMapper;
        this.communityMapper = communityMapper;
        this.commentMapper = commentMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ForumPostVO toPostVO(ForumPost post,
                                String userId,
                                User author,
                                ForumCommunity community,
                                long commentCount) {
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
        vo.setIsAuthor(userId != null
                && post.getUserId() != null
                && post.getUserId().equals(userId));

        vo.setTags(parseTagList(post));
        vo.setMedia(parseMedia(post));
        attachVoteAndStats(vo, post, userId, commentCount);
        attachCommunity(vo, community);
        attachAuthor(vo, author);
        attachMembership(vo, post, userId);
        return vo;
    }

    @Override
    public ForumPostVO toPostVO(ForumPost post, String userId, User author) {
        ForumCommunity community = post.getCommunityId() != null
                ? communityMapper.selectById(post.getCommunityId())
                : null;
        long realCommentCount = post.getId() != null
                ? commentMapper.countByPostId(post.getId())
                : 0L;
        return toPostVO(post, userId, author, community, realCommentCount);
    }

    // ---- shaping helpers (internal to the projection) ----

    @SuppressWarnings("unchecked")
    private List<String> parseTagList(ForumPost post) {
        Object tags = post.getTags();
        if (tags instanceof List) {
            return (List<String>) tags;
        }
        if (tags instanceof String) {
            try {
                return objectMapper.readValue((String) tags, List.class);
            } catch (Exception e) {
                log.warn("Failed to parse tags JSON for post {}: {}",
                        post.getId(), e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    private Object parseMedia(ForumPost post) {
        Object media = post.getMedia();
        if (media instanceof String) {
            try {
                return objectMapper.readValue((String) media, Object.class);
            } catch (Exception e) {
                log.warn("Failed to parse media JSON for post {}: {}",
                        post.getId(), e.getMessage());
                return null;
            }
        }
        return media;
    }

    private void attachVoteAndStats(ForumPostVO vo, ForumPost post,
                                    String userId, long commentCount) {
        VoteResultVO vr = voteService.getVoteStatus(
                userId, post.getId(), EdgeOperationTargetType.FORUM_POST);
        vo.setVoteState(vr.getUserVote() == 1 ? "upvoted"
                : vr.getUserVote() == -1 ? "downvoted" : "neutral");

        LinkedHashMap<String, Object> statsMap = new LinkedHashMap<>();
        Object rawStats = post.getStats();
        if (rawStats instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existing = (Map<String, Object>) rawStats;
            statsMap.putAll(existing);
        } else if (rawStats instanceof String) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(
                        (String) rawStats, LinkedHashMap.class);
                statsMap.putAll(parsed);
            } catch (Exception e) {
                log.warn("Failed to parse stats JSON for post {}: {}",
                        post.getId(), e.getMessage());
            }
        }
        statsMap.put("likes", vr.getLikes());
        statsMap.put("dislikes", vr.getDislikes());
        statsMap.put("score", vr.getLikes() - vr.getDislikes());
        statsMap.put("comments", commentCount);
        vo.setStats(statsMap);
        vo.setCommentCount(commentCount);
    }

    private void attachCommunity(ForumPostVO vo, ForumCommunity community) {
        if (community != null) {
            vo.setCommunityName(community.getName());
            vo.setCommunitySlug(community.getSlug());
        }
    }

    private void attachAuthor(ForumPostVO vo, User author) {
        if (author != null) {
            vo.setAuthorUsername(author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }
    }

    private void attachMembership(ForumPostVO vo, ForumPost post, String userId) {
        if (userId != null) {
            vo.setIsMember(memberMapper.isMember(post.getCommunityId(), userId));
        }
    }
}
