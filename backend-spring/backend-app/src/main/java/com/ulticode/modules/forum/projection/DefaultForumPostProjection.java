package com.ulticode.modules.forum.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.dto.VoteStatusDTO;
import com.ulticode.app.api.service.ForumVoteReadPort;
import com.ulticode.modules.forum.dto.ForumPostVO;
import com.ulticode.modules.forum.entity.ForumCommunity;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMapper;
import com.ulticode.modules.forum.mapper.ForumCommunityMemberMapper;
import com.ulticode.modules.forum.port.ForumUserReadPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link ForumPostProjection} implementation.
 *
 * <p>Owns every collaborator the projection needs (mappers, vote port,
 * ObjectMapper for JSON normalisation) so callers no longer thread four to
 * six parameters through.
 *
 * <p>P7-RELOCATE-FORUM-001: {@code VoteService} replaced with
 * {@link ForumVoteReadPort}; {@code User} replaced with
 * {@link ForumUserReadPort.UserSummary}.
 *
 * @author ulticode
 */
@Slf4j
@Component
public class DefaultForumPostProjection implements ForumPostProjection {

    private final ForumVoteReadPort forumVoteReadPort;
    private final ForumCommunityMemberMapper memberMapper;
    private final ForumCommunityMapper communityMapper;
    private final ForumCommentMapper commentMapper;
    private final ObjectMapper objectMapper;

    public DefaultForumPostProjection(ForumVoteReadPort forumVoteReadPort,
                                     ForumCommunityMemberMapper memberMapper,
                                     ForumCommunityMapper communityMapper,
                                     ForumCommentMapper commentMapper,
                                     ObjectMapper objectMapper) {
        this.forumVoteReadPort = forumVoteReadPort;
        this.memberMapper = memberMapper;
        this.communityMapper = communityMapper;
        this.commentMapper = commentMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public ForumPostVO toPostVO(ForumPost post,
                                String userId,
                                ForumUserReadPort.UserSummary author,
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
    public ForumPostVO toPostVO(ForumPost post, String userId, ForumUserReadPort.UserSummary author) {
        ForumCommunity community = post.getCommunityId() != null
                ? communityMapper.selectById(post.getCommunityId())
                : null;
        long realCommentCount = post.getId() != null
                ? commentMapper.countByPostId(post.getId())
                : 0L;
        return toPostVO(post, userId, author, community, realCommentCount);
    }

    // ---- shaping helpers ----

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
        VoteStatusDTO vr = forumVoteReadPort.getVoteStatus(
                userId, post.getId(), "FORUM_POST");
        vo.setVoteState(vr.userVote() == 1 ? "upvoted"
                : vr.userVote() == -1 ? "downvoted" : "neutral");

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
        statsMap.put("likes", vr.likes());
        statsMap.put("dislikes", vr.dislikes());
        statsMap.put("score", vr.score());
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

    private void attachAuthor(ForumPostVO vo, ForumUserReadPort.UserSummary author) {
        if (author != null) {
            vo.setAuthorUsername(author.username());
            vo.setAuthorAvatar(author.avatar());
        }
    }

    private void attachMembership(ForumPostVO vo, ForumPost post, String userId) {
        if (userId != null) {
            vo.setIsMember(memberMapper.isMember(post.getCommunityId(), userId));
        }
    }
}
