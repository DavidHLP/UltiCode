package com.ulticode.modules.solution.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.achievement.entity.Achievement;
import com.ulticode.modules.achievement.entity.UserAchievement;
import com.ulticode.modules.achievement.mapper.AchievementMapper;
import com.ulticode.modules.achievement.mapper.UserAchievementMapper;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.solution.dto.SolutionCommentVO;
import com.ulticode.modules.solution.dto.SolutionListItemVO;
import com.ulticode.modules.solution.dto.SolutionVO;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.vote.entity.EdgeOperation;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
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
 * The single adapter behind {@link SolutionProjection}. Injects the same read-side mappers the
 * solution read cluster used before the deepening (solution, comment, user, edge-operation,
 * problem-tag and achievement mappers), and owns the JSON/CSV tag parsing helper.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSolutionProjection implements SolutionProjection {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SolutionMapper solutionMapper;
    private final SolutionCommentMapper solutionCommentMapper;
    private final UserMapper userMapper;
    private final EdgeOperationMapper edgeOperationMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ProblemTagMapper problemTagMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final AchievementMapper achievementMapper;

    @Override
    public List<SolutionCommentVO> getComments(String solutionId) {
        // Verify solution exists (mirrors the pre-refactor findById guard).
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

        // Fetch author info
        if (comment.getUserId() != null) {
            User author = userMapper.selectById(comment.getUserId());
            if (author != null) {
                vo.setAuthorUsername(author.getName() != null ? author.getName() : author.getUsername());
                vo.setAuthorAvatar(author.getAvatar());
            }
        }

        return vo;
    }

    @Override
    public PageResult<SolutionListItemVO> findByProblemId(Long problemId, Integer page, Integer pageSize) {
        // Set default pagination values
        int currentPage = (page != null && page > 0) ? page : 1;
        int currentPageSize = (pageSize != null && pageSize > 0) ? pageSize : 20;

        // Limit page size to prevent large queries
        currentPageSize = Math.min(currentPageSize, 100);

        // Build query wrapper
        LambdaQueryWrapper<Solution> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Solution::getProblemId, problemId)
                .eq(Solution::getIsPublished, true)
                .orderByDesc(Solution::getCreatedAt);

        // Execute paginated query
        Page<Solution> solutionPage = new Page<>(currentPage, currentPageSize);
        Page<Solution> result = solutionMapper.selectPage(solutionPage, queryWrapper);

        List<Solution> records = result.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), currentPage, currentPageSize);
        }

        // Batch-fetch all related data to eliminate N+1 queries
        List<String> solutionIds = records.stream().map(Solution::getId).toList();
        List<String> userIds = records.stream().map(Solution::getUserId).distinct().toList();

        // Batch query users
        Map<String, User> userMap = userIds.stream()
                .map(userMapper::selectById)
                .filter(u -> u != null)
                .collect(Collectors.toMap(User::getId, u -> u));

        // Batch query vote counts
        String targetType = EdgeOperationTargetType.SOLUTION.getValue();
        List<Map<String, Object>> likeCounts = edgeOperationMapper.countByTargetsAndOperation(
                solutionIds, targetType, EdgeOperationType.VOTE_UP.getValue());
        List<Map<String, Object>> dislikeCounts = edgeOperationMapper.countByTargetsAndOperation(
                solutionIds, targetType, EdgeOperationType.VOTE_DOWN.getValue());

        Map<String, Long> likesMap = likeCounts.stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("target_id"),
                        m -> ((Number) m.get("cnt")).longValue(),
                        (a, b) -> a));
        Map<String, Long> dislikesMap = dislikeCounts.stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("target_id"),
                        m -> ((Number) m.get("cnt")).longValue(),
                        (a, b) -> a));

        // Batch query comment counts
        Map<String, Long> commentCounts = solutionIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> (long) solutionCommentMapper.countBySolutionId(id)));

        // Batch query viewer votes
        String currentUserId = SecurityUtil.getCurrentUserId();
        final Map<String, Integer> viewerVoteMap;
        if (currentUserId != null) {
            List<Map<String, Object>> viewerVotes = edgeOperationMapper.findByOperatorAndTargets(
                    currentUserId, solutionIds, targetType);
            viewerVoteMap = viewerVotes.stream()
                    .collect(Collectors.toMap(
                            m -> (String) m.get("target_id"),
                            m -> {
                                String opType = (String) m.get("operation_type");
                                if (EdgeOperationType.VOTE_UP.getValue().equals(opType)) {
                                    return 1;
                                } else if (EdgeOperationType.VOTE_DOWN.getValue().equals(opType)) {
                                    return -1;
                                }
                                return 0;
                            },
                            (a, b) -> a));
        } else {
            viewerVoteMap = Collections.emptyMap();
        }

        // Convert to lightweight VO
        List<SolutionListItemVO> voList = records.stream()
                .map(s -> toListItemVO(s, userMap, likesMap, dislikesMap, commentCounts, viewerVoteMap))
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), currentPage, currentPageSize);
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
        return solutions.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * Convert a Solution entity to a lightweight SolutionListItemVO using pre-fetched batch data.
     */
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

        // Author info from batch-fetched user map
        User author = userMap.get(solution.getUserId());
        if (author != null) {
            SolutionListItemVO.AuthorInfo authorInfo = new SolutionListItemVO.AuthorInfo();
            authorInfo.setId(author.getId());
            authorInfo.setName(author.getName() != null ? author.getName() : author.getUsername());
            authorInfo.setAvatar(author.getAvatar());
            vo.setAuthor(authorInfo);
        }

        // Counts from batch-fetched maps
        SolutionListItemVO.Counts counts = new SolutionListItemVO.Counts();
        counts.setViews(solution.getViews());
        counts.setLikes(likesMap.getOrDefault(solution.getId(), 0L));
        counts.setDislikes(dislikesMap.getOrDefault(solution.getId(), 0L));
        counts.setComments(commentCounts.getOrDefault(solution.getId(), 0L));
        vo.setCounts(counts);

        // Score
        long likes = likesMap.getOrDefault(solution.getId(), 0L);
        long dislikes = dislikesMap.getOrDefault(solution.getId(), 0L);
        vo.setScore(likes - dislikes);

        // Viewer vote from batch-fetched map
        vo.setViewerVote(viewerVoteMap.getOrDefault(solution.getId(), 0));

        return vo;
    }

    @Override
    public SolutionVO toVO(Solution solution) {
        return toVO(solution, null);
    }

    @Override
    public SolutionVO toVO(Solution solution, String currentUserId) {
        if (solution == null) {
            return null;
        }

        SolutionVO vo = new SolutionVO();
        BeanUtils.copyProperties(solution, vo);

        // Parse tags JSON to list
        vo.setTags(parseTags(solution.getTags()));

        // Fetch author info
        User author = userMapper.selectById(solution.getUserId());
        if (author != null) {
            vo.setAuthorName(author.getName() != null ? author.getName() : author.getUsername());
            vo.setAuthorAvatar(author.getAvatar());
        }

        // Populate vote counts from edge_operations
        String targetId = solution.getId();
        String targetType = EdgeOperationTargetType.SOLUTION.getValue();
        long likes = edgeOperationMapper.countByTargetAndOperation(
                targetId, targetType, EdgeOperationType.VOTE_UP.getValue());
        long dislikes = edgeOperationMapper.countByTargetAndOperation(
                targetId, targetType, EdgeOperationType.VOTE_DOWN.getValue());
        long commentCount = solutionCommentMapper.countBySolutionId(targetId);

        vo.setLikes(likes);
        vo.setDislikes(dislikes);
        vo.setComments(commentCount);
        vo.setScore(likes - dislikes);

        // Populate current user's vote state
        if (currentUserId != null) {
            LambdaQueryWrapper<EdgeOperation> voteWrapper = new LambdaQueryWrapper<>();
            voteWrapper.eq(EdgeOperation::getOperatorId, currentUserId)
                    .eq(EdgeOperation::getTargetId, targetId)
                    .eq(EdgeOperation::getTargetType, targetType);
            EdgeOperation userVote = edgeOperationMapper.selectOne(voteWrapper);
            if (userVote != null) {
                if (userVote.getOperationType() == EdgeOperationType.VOTE_UP) {
                    vo.setUserVote(1);
                } else if (userVote.getOperationType() == EdgeOperationType.VOTE_DOWN) {
                    vo.setUserVote(-1);
                } else {
                    vo.setUserVote(0);
                }
            } else {
                vo.setUserVote(0);
            }
        }

        // Populate topic name from problem tags
        List<String> tagIds = problemTagRelationMapper.findTagIdsByProblemId(solution.getProblemId());
        if (tagIds != null && !tagIds.isEmpty()) {
            ProblemTag firstTag = problemTagMapper.selectById(tagIds.get(0));
            if (firstTag != null) {
                vo.setTopicName(firstTag.getLabel());
            }
        }

        // Populate user badges from achievements
        List<UserAchievement> userAchievements = userAchievementMapper.findByUserId(solution.getUserId());
        if (userAchievements != null && !userAchievements.isEmpty()) {
            List<String> badgeNames = userAchievements.stream()
                    .map(ua -> {
                        Achievement achievement = achievementMapper.selectById(ua.getAchievementId());
                        return achievement != null ? achievement.getName() : null;
                    })
                    .filter(Objects::nonNull)
                    .limit(3)
                    .collect(Collectors.toList());
            if (!badgeNames.isEmpty()) {
                vo.setBadges(badgeNames);
                vo.setFlair(badgeNames.get(0));
            }
        }

        return vo;
    }

    /**
     * Parse tags stored string back to list.
     * Supports both new comma-separated format ("a,b,c") and legacy JSON array ("[\"a\",\"b\"]")
     * for backward compatibility with rows written by the previous implementation.
     *
     * @param tagsStr the stored tags string
     * @return list of tags (empty when blank)
     */
    private List<String> parseTags(String tagsStr) {
        if (tagsStr == null || tagsStr.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = tagsStr.trim();
        // Legacy: JSON array (best-effort parse, then fall through to comma split)
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
