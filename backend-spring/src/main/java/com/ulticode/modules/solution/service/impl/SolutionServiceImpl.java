package com.ulticode.modules.solution.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.annotation.CheckBan;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
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
import com.ulticode.modules.solution.projection.SolutionProjection;
import com.ulticode.modules.solution.service.SolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of SolutionService.
 *
 * <p>Holds the solution and comment write state machine plus the detail read that records a view.
 * Every entity-to-VO shaping call is delegated to {@link SolutionProjection} so the write paths
 * never carry a private copy of the projection rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SolutionServiceImpl implements SolutionService {

    private final SolutionMapper solutionMapper;
    private final SolutionCommentMapper solutionCommentMapper;
    private final ProblemMapper problemMapper;
    private final SolutionProjection solutionProjection;
    private final Clock clock;

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
        // Silently no-op when solution is missing: this endpoint is permitAll
        // (埋点路径，期望静默成功，避免污染 5xx-equivalent error code 与监控告警)
        Optional<Solution> opt = findById(solutionId);
        if (opt.isEmpty()) {
            log.debug("recordView: solution {} not found, skip (user {})", solutionId, userId);
            return;
        }
        Solution solution = opt.get();

        // Increment view count
        solution.setViews(solution.getViews() != null ? solution.getViews() + 1 : 1);
        solutionMapper.updateById(solution);

        log.info("Solution view recorded: {} by user {}", solutionId, userId);
    }

    @Override
    @Transactional
    @CheckBan
    public SolutionCommentVO createComment(String solutionId, String userId, CreateSolutionCommentDTO dto) {
        findById(solutionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOLUTION_NOT_FOUND));

        SolutionComment comment = new SolutionComment();
        comment.setId(UUID.randomUUID().toString());
        comment.setSolutionId(solutionId);
        comment.setUserId(userId);
        comment.setContent(dto.getContent());
        comment.setParentId(dto.getParentId());
        comment.setCreatedAt(LocalDateTime.now(clock));
        comment.setUpdatedAt(LocalDateTime.now(clock));
        comment.setIsFlagged(false);
        comment.setIsDeleted(false);

        solutionCommentMapper.insert(comment);
        return solutionProjection.toCommentVO(comment);
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
        comment.setUpdatedAt(LocalDateTime.now(clock));
        solutionCommentMapper.updateById(comment);
        return solutionProjection.toCommentVO(comment);
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
        comment.setDeletedAt(LocalDateTime.now(clock));
        comment.setDeletedBy(userId);
        solutionCommentMapper.updateById(comment);
    }

    @Override
    public SolutionVO getSolutionById(String id) {
        return getSolutionById(id, null);
    }

    @Override
    public SolutionVO getSolutionById(String id, String currentUserId) {
        Solution solution = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SOLUTION_NOT_FOUND));

        // Increment view count
        solution.setViews(solution.getViews() != null ? solution.getViews() + 1 : 1);
        solutionMapper.updateById(solution);

        return solutionProjection.toVO(solution, currentUserId);
    }

    @Override
    @Transactional
    @CheckBan
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
        solution.setTags(joinTags(createDTO.getTags()));
        solution.setViews(0);
        solution.setLikes(0);
        solution.setDislikes(0);
        solution.setCommentCount(0);
        solution.setIsPublished(true);
        solution.setPublishedAt(LocalDateTime.now(clock));
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
        return solutionProjection.toVO(solution);
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
            solution.setTags(joinTags(updateDTO.getTags()));
        }

        solutionMapper.updateById(solution);

        log.info("Solution updated: {} by user {}", id, userId);
        return solutionProjection.toVO(solution);
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

    /**
     * Join a tag list into the storage format (comma-separated, empty string for null/empty).
     *
     * @param tags the tag list
     * @return storage string
     */
    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return String.join(",", tags);
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
