package com.ulticode.modules.solution.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.solution.dto.SolutionCommentVO;
import com.ulticode.modules.solution.dto.SolutionListItemVO;
import com.ulticode.modules.solution.dto.SolutionVO;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.solution.port.AchievementBadgeReadPort;
import com.ulticode.modules.solution.port.ProblemTagReadPort;
import com.ulticode.modules.solution.port.SolutionVoteReadPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The single adapter behind {@link SolutionProjection}. Cross-domain
 * reads now go through consumer-owned ports (the {@code solution}
 * module defines them; the {@code user}, {@code vote}, {@code problem}
 * and {@code achievement} modules provide the adapters). No
 * cross-module mapper is imported here.
 *
 * <p>Performance note: author lookups on a list page use
 * {@link UserReadProjection#findAllById} (one batched SELECT) instead of
 * the previous per-row {@code userMapper.selectById} N+1.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSolutionProjection implements SolutionProjection {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SolutionMapper solutionMapper;
    private final SolutionCommentMapper solutionCommentMapper;
    private final UserReadProjection userReadProjection;
    private final SolutionVoteReadPort voteReadPort;
    private final ProblemTagReadPort problemTagReadPort;
    private final AchievementBadgeReadPort achievementBadgeReadPort;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public List<SolutionCommentVO> getComments(String solutionId) {
        if (solutionMapper.selectById(solutionId) == null) {
            throw new BusinessException(ErrorCode.SOLUTION_NOT_FOUND);
        }

        LambdaQueryWrapper<SolutionComment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SolutionComment::getSolutionId, solutionId)
                .orderByAsc(SolutionComment::getCreatedAt);

        List<SolutionComment> comments = solutionCommentMapper.selectList(queryWrapper);

        return comments.stream()
                .map(this::toCommentVO)
                .collect(Collectors.toList());
    }

    @Override
    public SolutionCommentVO toCommentVO(SolutionComment comment) {
        if (comment == null) {
            return null;
        }

        SolutionCommentVO vo = new SolutionCommentVO();
        vo.setId(comment.getId());
        vo.setSolutionId(comment.getSolutionId());
        vo.setParentId(comment.getParentId());
        vo.setUserId(comment.getUserId());
        vo.setAuthorId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setUpdatedAt(comment.getUpdatedAt());
        vo.setIsFlagged(comment.getIsFlagged());

        if (comment.getUserId() != null) {
            User author = userReadProjection.findById(comment.getUserId()).orElse(null);
            if (author != null) {
                vo.setAuthorUsername(author.getName() != null ? author.getName() : author.getUsername());
                vo.setAuthorAvatar(author.getAvatar());
            }
        }

        return vo;
    }

    @Override
    public PageResult<SolutionListItemVO> findByProblemId(Long problemId, Integer page, Integer pageSize) {
        PaginationRequest pageRequest = PaginationRequest.of(page, pageSize);

        LambdaQueryWrapper<Solution> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Solution::getProblemId, problemId)
                .eq(Solution::getIsPublished, true)
                .orderByDesc(Solution::getCreatedAt);

        Page<Solution> solutionPage = new Page<>(pageRequest.page(), pageRequest.pageSize());
        Page<Solution> result = solutionMapper.selectPage(solutionPage, queryWrapper);

        List<Solution> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageRequest);
        }

        List<String> solutionIds = records.stream().map(Solution::getId).toList();
        List<String> userIds = records.stream().map(Solution::getUserId).distinct().toList();

        // Batch author fetch — one round-trip, kills the per-row N+1.
        Map<String, User> userMap = userReadProjection.findAllById(userIds);

        // Batch vote counts via the consumer-owned port.
        Map<String, Long> likesMap = voteReadPort.countLikesByTargets(solutionIds);
        Map<String, Long> dislikesMap = voteReadPort.countDislikesByTargets(solutionIds);

        Map<String, Long> commentCounts = solutionIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> (long) solutionCommentMapper.countBySolutionId(id)));

        String currentUserId = currentUserProvider.getCurrentUserId();
        Map<String, Integer> viewerVoteMap = currentUserId == null
                ? Collections.emptyMap()
                : voteReadPort.viewerVotes(currentUserId, solutionIds);

        List<SolutionListItemVO> voList = records.stream()
                .map(s -> toListItemVO(s, userMap, likesMap, dislikesMap, commentCounts, viewerVoteMap))
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageRequest);
    }

    @Override
    public List<SolutionVO> findByUserId(String userId, Long problemId) {
        LambdaQueryWrapper<Solution> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Solution::getUserId, userId)
                .eq(Solution::getIsPublished, true)
                .orderByDesc(Solution::getCreatedAt);

        if (problemId != null) {
            queryWrapper.eq(Solution::getProblemId, problemId);
        }

        List<Solution> solutions = solutionMapper.selectList(queryWrapper);
        if (solutions.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch topic-name resolution so the list does not pay a per-row
        // N+1 for problem-tag labels.
        List<Long> problemIds = solutions.stream()
                .map(Solution::getProblemId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> topicByProblem = problemTagReadPort.findFirstTagLabels(problemIds);

        return solutions.stream()
                .map(s -> toVO(s, null, topicByProblem))
                .collect(Collectors.toList());
    }

    private SolutionListItemVO toListItemVO(
            Solution solution,
            Map<String, User> userMap,
            Map<String, Long> likesMap,
            Map<String, Long> dislikesMap,
            Map<String, Long> commentCounts,
            Map<String, Integer> viewerVoteMap) {
        if (solution == null) {
            return null;
        }

        SolutionListItemVO vo = new SolutionListItemVO();
        vo.setId(solution.getId());
        vo.setProblemId(solution.getProblemId());
        vo.setTitle(solution.getTitle());
        vo.setSummary(solution.getSummary());
        vo.setLanguage(solution.getLanguage());
        vo.setTags(parseTags(solution.getTags()));
        vo.setPublishedAt(solution.getPublishedAt());
        vo.setIsPinned(solution.getIsPinned());

        User author = userMap.get(solution.getUserId());
        if (author != null) {
            SolutionListItemVO.AuthorInfo authorInfo = new SolutionListItemVO.AuthorInfo();
            authorInfo.setId(author.getId());
            authorInfo.setName(author.getName() != null ? author.getName() : author.getUsername());
            authorInfo.setAvatar(author.getAvatar());
            vo.setAuthor(authorInfo);
        }

        SolutionListItemVO.Counts counts = new SolutionListItemVO.Counts();
        counts.setViews(solution.getViews());
        counts.setLikes(likesMap.getOrDefault(solution.getId(), 0L));
        counts.setDislikes(dislikesMap.getOrDefault(solution.getId(), 0L));
        counts.setComments(commentCounts.getOrDefault(solution.getId(), 0L));
        vo.setCounts(counts);

        long likes = likesMap.getOrDefault(solution.getId(), 0L);
        long dislikes = dislikesMap.getOrDefault(solution.getId(), 0L);
        vo.setScore(likes - dislikes);
        vo.setViewerVote(viewerVoteMap.getOrDefault(solution.getId(), 0));

        return vo;
    }

    @Override
    public SolutionVO toVO(Solution solution) {
        return toVO(solution, null, null);
    }

    @Override
    public SolutionVO toVO(Solution solution, String currentUserId) {
        return toVO(solution, currentUserId, null);
    }

    /**
     * Shape a {@link Solution} into its VO. When {@code topicByProblem} is
     * non-null the topic name is read from it (batched by the list caller);
     * otherwise it falls back to a single {@link ProblemTagReadPort} lookup.
     */
    private SolutionVO toVO(Solution solution, String currentUserId, Map<Long, String> topicByProblem) {
        if (solution == null) {
            return null;
        }

        SolutionVO vo = new SolutionVO();
        BeanUtils.copyProperties(solution, vo);
        vo.setTags(parseTags(solution.getTags()));

        User author = userReadProjection.findById(solution.getUserId()).orElse(null);
        if (author != null) {
            vo.setAuthorName(author.getName() != null ? author.getName() : author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        String solutionId = solution.getId();
        long likes = voteReadPort.countLikes(solutionId);
        long dislikes = voteReadPort.countDislikes(solutionId);
        long commentCount = solutionCommentMapper.countBySolutionId(solutionId);

        vo.setLikes(likes);
        vo.setDislikes(dislikes);
        vo.setComments(commentCount);
        vo.setScore(likes - dislikes);

        if (currentUserId != null) {
            Map<String, Integer> mine = voteReadPort.viewerVotes(currentUserId,
                    Collections.singletonList(solutionId));
            Integer my = mine.get(solutionId);
            vo.setUserVote(my == null ? 0 : my);
        }

        // Topic from the first tag attached to the problem.
        String topicName = topicByProblem != null
                ? topicByProblem.get(solution.getProblemId())
                : problemTagReadPort.findFirstTagLabel(solution.getProblemId());
        vo.setTopicName(topicName);

        // Badges from the author.
        List<String> badgeNames = achievementBadgeReadPort.findBadgeNames(solution.getUserId(), 3);
        if (!badgeNames.isEmpty()) {
            vo.setBadges(badgeNames);
            vo.setFlair(badgeNames.get(0));
        }

        return vo;
    }

    /**
     * Parse tags stored string back to list. Supports both the new
     * comma-separated format ("a,b,c") and the legacy JSON array
     * ("[\"a\",\"b\"]") for backward compatibility with rows written by
     * the previous implementation.
     */
    private List<String> parseTags(String tagsStr) {
        if (tagsStr == null || tagsStr.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = tagsStr.trim();
        if (trimmed.startsWith("[")) {
            try {
                return OBJECT_MAPPER.readValue(trimmed, new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                // Log the failure class only — do not echo the raw input
                // (tags may be user-controlled; do not risk PII leak via logs).
                log.warn("Failed to parse legacy tags JSON (length={}), falling back to comma split", trimmed.length(), e);
                // fall through to comma split below
            }
        }
        return Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}