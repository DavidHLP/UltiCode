package com.ulticode.modules.solution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.solution.dto.CreateSolutionCommentDTO;
import com.ulticode.modules.solution.dto.CreateSolutionDTO;
import com.ulticode.modules.solution.dto.SolutionCommentVO;
import com.ulticode.modules.solution.dto.SolutionVO;
import com.ulticode.modules.solution.dto.UpdateSolutionCommentDTO;
import com.ulticode.modules.solution.dto.UpdateSolutionDTO;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.solution.service.SolutionService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of SolutionService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolutionServiceImpl implements SolutionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SolutionMapper solutionMapper;
    private final SolutionCommentMapper solutionCommentMapper;
    private final UserMapper userMapper;
    private final ProblemMapper problemMapper;
    private final EdgeOperationMapper edgeOperationMapper;

    private static final int MAX_SUMMARY_LENGTH = 180;

    @Override
    public Optional<Solution> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(solutionMapper.selectById(id));
    }

    @Override
    public void recordView(String solutionId, String userId) {
        Solution solution = findById(solutionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOLUTION_NOT_FOUND));

        // Increment view count
        solution.setViews(solution.getViews() != null ? solution.getViews() + 1 : 1);
        solutionMapper.updateById(solution);

        log.info("Solution view recorded: {} by user {}", solutionId, userId);
    }

    @Override
    public List<SolutionCommentVO> getComments(String solutionId) {
        // Verify solution exists
        findById(solutionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOLUTION_NOT_FOUND));

        LambdaQueryWrapper<SolutionComment> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SolutionComment::getSolutionId, solutionId)
                .orderByAsc(SolutionComment::getCreatedAt);

        List<SolutionComment> comments = solutionCommentMapper.selectList(queryWrapper);

        return comments.stream()
                .map(this::toCommentVO)
                .collect(Collectors.toList());
    }

    /**
     * Convert SolutionComment entity to SolutionCommentVO.
     *
     * @param comment the entity
     * @return the VO
     */
    private SolutionCommentVO toCommentVO(SolutionComment comment) {
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
    @Transactional
    public SolutionCommentVO createComment(String solutionId, String userId, CreateSolutionCommentDTO dto) {
        findById(solutionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOLUTION_NOT_FOUND));

        SolutionComment comment = new SolutionComment();
        comment.setId(UUID.randomUUID().toString());
        comment.setSolutionId(solutionId);
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setParentId(dto.getParentId());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedAt(LocalDateTime.now());
        comment.setIsFlagged(false);
        comment.setIsDeleted(false);

        solutionCommentMapper.insert(comment);
        return toCommentVO(comment);
    }

    @Override
    @Transactional
    public SolutionCommentVO updateComment(String commentId, String userId, UpdateSolutionCommentDTO dto) {
        SolutionComment comment = solutionCommentMapper.selectById(commentId);
        if (comment == null || Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new BusinessException(ErrorCode.SOLUTION_COMMENT_NOT_FOUND);
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.USER_CANNOT_EDIT_OTHERS);
        }

        comment.setContent(dto.getContent());
        comment.setUpdatedAt(LocalDateTime.now());
        solutionCommentMapper.updateById(comment);
        return toCommentVO(comment);
    }

    @Override
    @Transactional
    public void deleteComment(String commentId, String userId) {
        SolutionComment comment = solutionCommentMapper.selectById(commentId);
        if (comment == null || Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new BusinessException(ErrorCode.SOLUTION_COMMENT_NOT_FOUND);
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.USER_CANNOT_EDIT_OTHERS);
        }

        comment.setIsDeleted(true);
        comment.setDeletedAt(LocalDateTime.now());
        comment.setDeletedBy(userId);
        solutionCommentMapper.updateById(comment);
    }

    @Override
    public PageResult<SolutionVO> findByProblemId(Long problemId, Integer page, Integer pageSize) {
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

        // Convert to VO
        List<SolutionVO> solutionVOList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(solutionVOList, result.getTotal(), currentPage, currentPageSize);
    }

    @Override
    public SolutionVO getSolutionById(String id) {
        Solution solution = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOLUTION_NOT_FOUND));

        // Increment view count
        solution.setViews(solution.getViews() != null ? solution.getViews() + 1 : 1);
        solutionMapper.updateById(solution);

        return toVO(solution);
    }

    @Override
    @Transactional
    public SolutionVO create(Long problemId, String userId, CreateSolutionDTO createDTO) {
        // Verify problem exists
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            throw new BusinessException(ErrorCode.PROBLEM_NOT_FOUND);
        }

        // Check if user already has a solution for this problem
        LambdaQueryWrapper<Solution> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(Solution::getProblemId, problemId)
                .eq(Solution::getUserId, userId);
        Solution existing = solutionMapper.selectOne(existingWrapper);
        if (existing != null) {
            throw new BusinessException(ErrorCode.SOLUTION_ALREADY_EXISTS);
        }

        // Build summary from content
        String summary = buildSummary(createDTO.getContent());

        // Create solution
        Solution solution = new Solution();
        solution.setId(UUID.randomUUID().toString());
        solution.setProblemId(problemId);
        solution.setUserId(userId);
        solution.setTitle(createDTO.getTitle());
        solution.setContent(createDTO.getContent());
        solution.setSummary(summary);
        solution.setLanguage(createDTO.getLanguage());
        solution.setTags(createDTO.getTags() != null ? createDTO.getTags() : "[]");
        solution.setViews(0);
        solution.setLikes(0);
        solution.setDislikes(0);
        solution.setCommentCount(0);
        solution.setIsPublished(true);
        solution.setPublishedAt(LocalDateTime.now());
        solution.setPublishedBy(userId);
        solution.setIsFlagged(false);
        solution.setIsDeleted(false);

        solutionMapper.insert(solution);

        // Update problem's hasSolution flag
        if (!Boolean.TRUE.equals(problem.getHasSolution())) {
            problem.setHasSolution(true);
            problemMapper.updateById(problem);
        }

        log.info("Solution created: {} for problem {} by user {}", solution.getId(), problemId, userId);
        return toVO(solution);
    }

    @Override
    @Transactional
    public SolutionVO update(String id, String userId, UpdateSolutionDTO updateDTO) {
        Solution solution = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOLUTION_NOT_FOUND));

        // Check ownership
        if (!solution.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SOLUTION_CANNOT_UPDATE_OTHERS);
        }

        // Build summary from content
        String summary = buildSummary(updateDTO.getContent());

        // Update fields
        solution.setTitle(updateDTO.getTitle());
        solution.setContent(updateDTO.getContent());
        solution.setSummary(summary);
        solution.setLanguage(updateDTO.getLanguage());
        if (updateDTO.getTags() != null) {
            solution.setTags(updateDTO.getTags());
        }

        solutionMapper.updateById(solution);

        log.info("Solution updated: {} by user {}", id, userId);
        return toVO(solution);
    }

    @Override
    @Transactional
    public void delete(String id, String userId) {
        Solution solution = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOLUTION_NOT_FOUND));

        // Check ownership
        if (!solution.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.SOLUTION_CANNOT_DELETE_OTHERS);
        }

        Long problemId = solution.getProblemId();

        // Soft delete is handled by MyBatis-Plus @TableLogic
        solutionMapper.deleteById(id);

        // Check if there are remaining solutions for this problem
        LambdaQueryWrapper<Solution> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(Solution::getProblemId, problemId);
        long remainingCount = solutionMapper.selectCount(countWrapper);

        if (remainingCount == 0) {
            // Update problem's hasSolution flag
            Problem problem = problemMapper.selectById(problemId);
            if (problem != null && Boolean.TRUE.equals(problem.getHasSolution())) {
                problem.setHasSolution(false);
                problemMapper.updateById(problem);
            }
        }

        log.info("Solution deleted: {} by user {}", id, userId);
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

    @Override
    public SolutionVO toVO(Solution solution) {
        if (solution == null) {
            return null;
        }

        SolutionVO vo = new SolutionVO();
        BeanUtils.copyProperties(solution, vo);

        // Parse tags JSON to list
        vo.setTagsList(parseTags(solution.getTags()));

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

        return vo;
    }

    /**
     * Parse tags JSON string to list.
     *
     * @param tagsJson the JSON string of tags
     * @return list of tags
     */
    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(tagsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tags JSON: {}", tagsJson, e);
            return Collections.emptyList();
        }
    }

    /**
     * Build a summary from markdown content.
     * Strips markdown formatting and truncates to max length.
     *
     * @param content the markdown content
     * @return the summary string
     */
    private String buildSummary(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        // Remove code blocks
        String plain = content
                .replaceAll("```[\\s\\S]*?```", "")
                .replaceAll("`[^`]*`", "")
                // Remove images
                .replaceAll("!\\[[^\\]]*]\\([^)]+\\)", "")
                // Remove links but keep text
                .replaceAll("\\[[^\\]]*]\\([^)]+\\)", "")
                // Remove markdown formatting
                .replaceAll("[#>*_~`>-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (plain.isEmpty()) {
            return "";
        }

        if (plain.length() <= MAX_SUMMARY_LENGTH) {
            return plain;
        }

        return plain.substring(0, MAX_SUMMARY_LENGTH).trim() + "...";
    }
}
