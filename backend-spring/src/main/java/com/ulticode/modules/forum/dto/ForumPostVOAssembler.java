package com.ulticode.modules.forum.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-static assembler for {@link ForumPostVO}. Takes every collaborator as a
 * method parameter — no Spring dependency, no field state. Used by both the
 * read projection (batch + single paths) and the write service
 * (createPost / updatePost) so the entity-to-VO rules live in exactly one place.
 *
 * <p>This class exists because {@code DefaultForumReadProjection} and
 * {@code ForumPostServiceImpl} must not depend on each other (Spring Boot 3.x
 * forbids constructor-injection cycles). The assembler is the third-party
 * they both reach into without holding a reference to each other.
 *
 * @author ulticode
 */
@Slf4j
public final class ForumPostVOAssembler {

    private ForumPostVOAssembler() {
        // utility class
    }

    /**
     * Hot batch path. Caller pre-resolves community + commentCount via batch
     * loaders (e.g. {@code DefaultForumReadProjection.batchLoadAuthors},
     * {@code batchLoadCommentCounts}) so this method does no SQL.
     */
    public static ForumPostVO toPostVO(ForumPost post,
                                       String userId,
                                       User author,
                                       ForumCommunity community,
                                       long commentCount,
                                       VoteService voteService,
                                       ForumCommunityMemberMapper memberMapper) {
        return assemble(post, userId, author, community, commentCount, voteService, memberMapper);
    }

    /**
     * Single-item path. Resolves community + comment-count individually when
     * the caller has not batched them (used by write paths and the single-post
     * read).
     */
    public static ForumPostVO toPostVO(ForumPost post,
                                       String userId,
                                       User author,
                                       VoteService voteService,
                                       ForumCommunityMapper communityMapper,
                                       ForumCommentMapper commentMapper,
                                       ForumCommunityMemberMapper memberMapper) {
        ForumCommunity community = post.getCommunityId() != null
                ? communityMapper.selectById(post.getCommunityId())
                : null;
        long realCommentCount = post.getId() != null
                ? commentMapper.countByPostId(post.getId())
                : 0L;
        return assemble(post, userId, author, community, realCommentCount, voteService, memberMapper);
    }

    private static ForumPostVO assemble(ForumPost post,
                                        String userId,
                                        User author,
                                        ForumCommunity community,
                                        long commentCount,
                                        VoteService voteService,
                                        ForumCommunityMemberMapper memberMapper) {
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

        // Tags — always normalised to List<String>
        Object tags = post.getTags();
        if (tags instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> tagList = (List<String>) tags;
            vo.setTags(tagList);
        } else if (tags instanceof String) {
            try {
                @SuppressWarnings("unchecked")
                List<String> parsed = new ObjectMapper().readValue((String) tags, List.class);
                vo.setTags(parsed);
            } catch (Exception e) {
                log.warn("Failed to parse tags JSON for post {}: {}", post.getId(), e.getMessage());
                vo.setTags(Collections.emptyList());
            }
        } else {
            vo.setTags(Collections.emptyList());
        }

        // Media — parse JSON string into Object, leave raw object as-is
        Object media = post.getMedia();
        if (media instanceof String) {
            try {
                media = new ObjectMapper().readValue((String) media, Object.class);
            } catch (Exception e) {
                log.warn("Failed to parse media JSON for post {}: {}", post.getId(), e.getMessage());
                media = null;
            }
        }
        vo.setMedia(media);

        // Vote state + stats
        VoteResultVO vr = voteService.getVoteStatus(userId, post.getId(), EdgeOperationTargetType.FORUM_POST);
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
                Map<String, Object> parsed = new ObjectMapper().readValue((String) rawStats, LinkedHashMap.class);
                statsMap.putAll(parsed);
            } catch (Exception e) {
                log.warn("Failed to parse stats JSON for post {}: {}", post.getId(), e.getMessage());
            }
        }
        statsMap.put("likes", vr.getLikes());
        statsMap.put("dislikes", vr.getDislikes());
        statsMap.put("score", vr.getLikes() - vr.getDislikes());
        statsMap.put("comments", commentCount);
        vo.setStats(statsMap);
        vo.setCommentCount(commentCount);

        // Community name / slug
        if (community != null) {
            vo.setCommunityName(community.getName());
            vo.setCommunitySlug(community.getSlug());
        }

        // Author
        if (author != null) {
            vo.setAuthorUsername(author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        // Membership
        if (userId != null) {
            vo.setIsMember(memberMapper.isMember(post.getCommunityId(), userId));
        }

        return vo;
    }
}