package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.dto.BulkCommentActionRequest;
import com.ulticode.modules.admin.service.AdminCommentService;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of AdminCommentService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommentServiceImpl implements AdminCommentService {

    private final ForumCommentMapper forumCommentMapper;
    private final SolutionCommentMapper solutionCommentMapper;
    private final UserMapper userMapper;
    private final ForumPostMapper forumPostMapper;
    private final SolutionMapper solutionMapper;

    @Override
    public PageResult<AdminCommentVO> getComments(AdminCommentQueryDTO query) {
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        // Determine which type to query
        String type = query.getType();
        if ("forum".equals(type)) {
            return getForumComments(query, page, limit);
        } else if ("solution".equals(type)) {
            return getSolutionComments(query, page, limit);
        } else {
            // Query both types and merge
            return getAllComments(query, page, limit);
        }
    }

    private PageResult<AdminCommentVO> getForumComments(AdminCommentQueryDTO query, int page, int limit) {
        LambdaQueryWrapper<ForumComment> wrapper = new LambdaQueryWrapper<>();

        // Search filter
        if (StringUtils.hasText(query.getSearch())) {
            wrapper.like(ForumComment::getBody, "%" + query.getSearch() + "%");
        }

        // Flagged status filter
        if (query.getIsFlagged() != null) {
            wrapper.eq(ForumComment::getIsFlagged, query.getIsFlagged());
        }

        // Deleted status filter
        if (query.getIsDeleted() != null) {
            wrapper.eq(ForumComment::getIsDeleted, query.getIsDeleted());
        }

        // Sorting
        boolean isAsc = !"desc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        if ("createdAt".equals(sortBy)) {
            wrapper.orderBy(true, isAsc, ForumComment::getCreatedAt);
        } else if ("flaggedAt".equals(sortBy)) {
            wrapper.orderBy(true, isAsc, ForumComment::getFlaggedAt);
        } else {
            wrapper.orderBy(true, isAsc, ForumComment::getCreatedAt);
        }

        Page<ForumComment> pageResult = new Page<>(page, limit);
        Page<ForumComment> result = forumCommentMapper.selectPage(pageResult, wrapper);

        List<AdminCommentVO> vos = result.getRecords().stream()
                .map(this::forumToAdminVO)
                .collect(Collectors.toList());

        return PageResult.of(vos, result.getTotal(), page, limit);
    }

    private PageResult<AdminCommentVO> getSolutionComments(AdminCommentQueryDTO query, int page, int limit) {
        LambdaQueryWrapper<SolutionComment> wrapper = new LambdaQueryWrapper<>();

        // Search filter
        if (StringUtils.hasText(query.getSearch())) {
            wrapper.like(SolutionComment::getContent, "%" + query.getSearch() + "%");
        }

        // Flagged status filter
        if (query.getIsFlagged() != null) {
            wrapper.eq(SolutionComment::getIsFlagged, query.getIsFlagged());
        }

        // Deleted status filter
        if (query.getIsDeleted() != null) {
            wrapper.eq(SolutionComment::getIsDeleted, query.getIsDeleted());
        }

        // Sorting
        boolean isAsc = !"desc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "createdAt";
        if ("createdAt".equals(sortBy)) {
            wrapper.orderBy(true, isAsc, SolutionComment::getCreatedAt);
        } else if ("flaggedAt".equals(sortBy)) {
            wrapper.orderBy(true, isAsc, SolutionComment::getFlaggedAt);
        } else {
            wrapper.orderBy(true, isAsc, SolutionComment::getCreatedAt);
        }

        Page<SolutionComment> pageResult = new Page<>(page, limit);
        Page<SolutionComment> result = solutionCommentMapper.selectPage(pageResult, wrapper);

        List<AdminCommentVO> vos = result.getRecords().stream()
                .map(this::solutionToAdminVO)
                .collect(Collectors.toList());

        return PageResult.of(vos, result.getTotal(), page, limit);
    }

    private PageResult<AdminCommentVO> getAllComments(AdminCommentQueryDTO query, int page, int limit) {
        // For simplicity, when type is not specified, fetch forum comments first
        // In a production system, you might want to implement proper merging
        return getForumComments(query, page, limit);
    }

    @Override
    public AdminCommentVO getComment(String id, String type) {
        if ("forum".equals(type)) {
            ForumComment comment = forumCommentMapper.selectById(id);
            if (comment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return forumToAdminVO(comment);
        } else if ("solution".equals(type)) {
            SolutionComment comment = solutionCommentMapper.selectById(id);
            if (comment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            return solutionToAdminVO(comment);
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST);
    }

    @Override
    public void flagComment(String id, String type, String reason) {
        if ("forum".equals(type)) {
            ForumComment comment = getForumCommentEntityOrThrow(id);
            comment.setIsFlagged(true);
            comment.setFlaggedReason(reason);
            comment.setFlaggedAt(LocalDateTime.now());
            forumCommentMapper.updateById(comment);
            log.info("Forum comment flagged: {}", id);
        } else if ("solution".equals(type)) {
            SolutionComment comment = getSolutionCommentEntityOrThrow(id);
            comment.setIsFlagged(true);
            comment.setFlaggedReason(reason);
            comment.setFlaggedAt(LocalDateTime.now());
            solutionCommentMapper.updateById(comment);
            log.info("Solution comment flagged: {}", id);
        }
    }

    @Override
    public void unflagComment(String id, String type) {
        if ("forum".equals(type)) {
            ForumComment comment = getForumCommentEntityOrThrow(id);
            comment.setIsFlagged(false);
            comment.setFlaggedReason(null);
            comment.setFlaggedAt(null);
            forumCommentMapper.updateById(comment);
            log.info("Forum comment unflagged: {}", id);
        } else if ("solution".equals(type)) {
            SolutionComment comment = getSolutionCommentEntityOrThrow(id);
            comment.setIsFlagged(false);
            comment.setFlaggedReason(null);
            comment.setFlaggedAt(null);
            solutionCommentMapper.updateById(comment);
            log.info("Solution comment unflagged: {}", id);
        }
    }

    @Override
    public void deleteComment(String id, String type) {
        if ("forum".equals(type)) {
            ForumComment comment = getForumCommentEntityOrThrow(id);
            comment.setIsDeleted(true);
            comment.setDeletedAt(LocalDateTime.now());
            forumCommentMapper.updateById(comment);
            log.info("Forum comment deleted: {}", id);
        } else if ("solution".equals(type)) {
            SolutionComment comment = getSolutionCommentEntityOrThrow(id);
            comment.setIsDeleted(true);
            comment.setDeletedAt(LocalDateTime.now());
            solutionCommentMapper.updateById(comment);
            log.info("Solution comment deleted: {}", id);
        }
    }

    @Override
    public BulkActionResult bulkCommentAction(BulkCommentActionRequest request) {
        BulkActionResult response = new BulkActionResult();
        response.setTotal(request.getIds().size());
        response.setResults(new ArrayList<>());
        response.setSuccessful(0);
        response.setFailed(0);

        for (String id : request.getIds()) {
            BulkActionResult.BulkActionItem item = new BulkActionResult.BulkActionItem();
            item.setId(id);

            try {
                switch (request.getAction()) {
                    case "delete" -> deleteComment(id, request.getType());
                    case "unflag" -> unflagComment(id, request.getType());
                    default -> throw new IllegalArgumentException("Unknown action: " + request.getAction());
                }
                item.setSuccess(true);
                response.setSuccessful(response.getSuccessful() + 1);
            } catch (Exception e) {
                log.error("Failed to perform action {} on comment {}", request.getAction(), id, e);
                item.setSuccess(false);
                item.setError(e.getMessage());
                response.setFailed(response.getFailed() + 1);
            }

            response.getResults().add(item);
        }

        return response;
    }

    /**
     * Get ForumComment entity or throw exception.
     */
    private ForumComment getForumCommentEntityOrThrow(String id) {
        ForumComment comment = forumCommentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return comment;
    }

    /**
     * Get SolutionComment entity or throw exception.
     */
    private SolutionComment getSolutionCommentEntityOrThrow(String id) {
        SolutionComment comment = solutionCommentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return comment;
    }

    /**
     * Convert ForumComment entity to AdminCommentVO.
     */
    private AdminCommentVO forumToAdminVO(ForumComment comment) {
        if (comment == null) {
            return null;
        }

        AdminCommentVO vo = new AdminCommentVO();
        vo.setId(comment.getId());
        vo.setContent(comment.getBody());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setUpdatedAt(comment.getEditedAt());
        vo.setAuthorId(comment.getAuthorId());
        vo.setParentCommentId(comment.getParentId());
        vo.setType("forum");
        vo.setParentId(comment.getPostId());
        vo.setIsFlagged(comment.getIsFlagged() != null ? comment.getIsFlagged() : false);
        vo.setFlaggedReason(comment.getFlaggedReason());
        vo.setFlaggedAt(comment.getFlaggedAt());
        vo.setIsDeleted(comment.getIsDeleted() != null ? comment.getIsDeleted() : false);
        vo.setDeletedAt(comment.getDeletedAt());
        vo.setDeletedBy(comment.getDeletedBy());

        // Fetch user info
        User user = userMapper.selectById(comment.getAuthorId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar());
        }

        // Fetch post title
        ForumPost post = forumPostMapper.selectById(comment.getPostId());
        if (post != null) {
            vo.setParentTitle(post.getTitle());
        }

        return vo;
    }

    /**
     * Convert SolutionComment entity to AdminCommentVO.
     */
    private AdminCommentVO solutionToAdminVO(SolutionComment comment) {
        if (comment == null) {
            return null;
        }

        AdminCommentVO vo = new AdminCommentVO();
        vo.setId(comment.getId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setUpdatedAt(comment.getUpdatedAt());
        vo.setAuthorId(comment.getUserId());
        vo.setParentCommentId(comment.getParentId());
        vo.setType("solution");
        vo.setParentId(comment.getSolutionId());
        vo.setIsFlagged(comment.getIsFlagged() != null ? comment.getIsFlagged() : false);
        vo.setFlaggedReason(comment.getFlaggedReason());
        vo.setFlaggedAt(comment.getFlaggedAt());
        vo.setIsDeleted(comment.getIsDeleted() != null ? comment.getIsDeleted() : false);
        vo.setDeletedAt(comment.getDeletedAt());
        vo.setDeletedBy(comment.getDeletedBy());

        // Fetch user info
        User user = userMapper.selectById(comment.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar());
        }

        // Fetch solution title
        Solution solution = solutionMapper.selectById(comment.getSolutionId());
        if (solution != null) {
            vo.setParentTitle(solution.getTitle());
        }

        return vo;
    }
}
