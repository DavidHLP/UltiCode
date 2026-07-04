package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.dto.AdminCommentVO;
import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.dto.BulkCommentActionRequest;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.admin.service.AdminCommentService;
import com.ulticode.modules.forum.entity.ForumComment;
import com.ulticode.modules.forum.mapper.ForumCommentMapper;
import com.ulticode.modules.solution.entity.SolutionComment;
import com.ulticode.modules.solution.mapper.SolutionCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCommentServiceImpl implements AdminCommentService {

    private static final Set<String> VALID_TYPES = Set.of("forum", "solution");

    private final ForumCommentMapper forumCommentMapper;
    private final SolutionCommentMapper solutionCommentMapper;
    private final AdminCommentReadPort commentReadPort;

    @Override
    public PageResult<AdminCommentVO> getComments(AdminCommentQueryDTO query) {
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        String type = query.getType();
        if (StringUtils.hasText(type) && !VALID_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid comment type: must be 'forum' or 'solution'");
        }

        if ("forum".equals(type)) {
            return getForumComments(query, page, limit);
        } else if ("solution".equals(type)) {
            return getSolutionComments(query, page, limit);
        } else {
            return getAllComments(query, page, limit);
        }
    }

    private PageResult<AdminCommentVO> getForumComments(AdminCommentQueryDTO query, int page, int limit) {
        Page<ForumComment> pageResult = new Page<>(page, limit);
        List<ForumComment> records = forumCommentMapper.selectPageIgnoreDeleted(
                pageResult, query.getIsFlagged(), query.getIsDeleted(), query.getSearch(),
                query.getParentEntityId(), query.getSortBy(), query.getSortOrder());
        pageResult.setRecords(records);

        Set<String> authorIds = records.stream()
                .map(ForumComment::getAuthorId).collect(Collectors.toSet());
        Set<String> postIds = records.stream()
                .map(ForumComment::getPostId).collect(Collectors.toSet());

        Map<String, AdminCommentReadPort.AuthorSummary> authorMap =
                commentReadPort.findAuthorSummariesByIds(authorIds);
        Map<String, String> postTitleMap = commentReadPort.findForumPostTitlesByIds(postIds);

        List<AdminCommentVO> vos = records.stream()
                .map(c -> forumToAdminVO(c, authorMap.get(c.getAuthorId()), postTitleMap.get(c.getPostId())))
                .collect(Collectors.toList());

        return PageResult.of(vos, pageResult.getTotal(), page, limit);
    }

    private PageResult<AdminCommentVO> getSolutionComments(AdminCommentQueryDTO query, int page, int limit) {
        Page<SolutionComment> pageResult = new Page<>(page, limit);
        List<SolutionComment> records = solutionCommentMapper.selectPageIgnoreDeleted(
                pageResult, query.getIsFlagged(), query.getIsDeleted(), query.getSearch(),
                query.getParentEntityId(), query.getSortBy(), query.getSortOrder());
        pageResult.setRecords(records);

        Set<String> authorIds = records.stream()
                .map(SolutionComment::getUserId).collect(Collectors.toSet());
        Set<String> solutionIds = records.stream()
                .map(SolutionComment::getSolutionId).collect(Collectors.toSet());

        Map<String, AdminCommentReadPort.AuthorSummary> authorMap =
                commentReadPort.findAuthorSummariesByIds(authorIds);
        Map<String, String> solutionTitleMap = commentReadPort.findSolutionTitlesByIds(solutionIds);

        List<AdminCommentVO> vos = records.stream()
                .map(c -> solutionToAdminVO(c, authorMap.get(c.getUserId()), solutionTitleMap.get(c.getSolutionId())))
                .collect(Collectors.toList());

        return PageResult.of(vos, pageResult.getTotal(), page, limit);
    }

    private PageResult<AdminCommentVO> getAllComments(AdminCommentQueryDTO query, int page, int limit) {
        Page<ForumComment> forumPage = new Page<>(1, Integer.MAX_VALUE);
        List<ForumComment> forumRecords = forumCommentMapper.selectPageIgnoreDeleted(
                forumPage, query.getIsFlagged(), query.getIsDeleted(), query.getSearch(),
                query.getParentEntityId(), query.getSortBy(), query.getSortOrder());
        forumPage.setRecords(forumRecords);

        Page<SolutionComment> solutionPage = new Page<>(1, Integer.MAX_VALUE);
        List<SolutionComment> solutionRecords = solutionCommentMapper.selectPageIgnoreDeleted(
                solutionPage, query.getIsFlagged(), query.getIsDeleted(), query.getSearch(),
                query.getParentEntityId(), query.getSortBy(), query.getSortOrder());
        solutionPage.setRecords(solutionRecords);

        Set<String> authorIds = new java.util.HashSet<>();
        forumRecords.forEach(c -> authorIds.add(c.getAuthorId()));
        solutionRecords.forEach(c -> authorIds.add(c.getUserId()));
        Set<String> postIds = forumRecords.stream().map(ForumComment::getPostId).collect(Collectors.toSet());
        Set<String> solutionIds = solutionRecords.stream().map(SolutionComment::getSolutionId).collect(Collectors.toSet());

        Map<String, AdminCommentReadPort.AuthorSummary> authorMap =
                commentReadPort.findAuthorSummariesByIds(authorIds);
        Map<String, String> postTitleMap = commentReadPort.findForumPostTitlesByIds(postIds);
        Map<String, String> solutionTitleMap = commentReadPort.findSolutionTitlesByIds(solutionIds);

        List<AdminCommentVO> all = new ArrayList<>();
        forumRecords.forEach(c -> all.add(forumToAdminVO(c, authorMap.get(c.getAuthorId()), postTitleMap.get(c.getPostId()))));
        solutionRecords.forEach(c -> all.add(solutionToAdminVO(c, authorMap.get(c.getUserId()), solutionTitleMap.get(c.getSolutionId()))));
        all.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));

        long total = all.size();
        int fromIndex = Math.min((page - 1) * limit, all.size());
        int toIndex = Math.min(fromIndex + limit, all.size());
        List<AdminCommentVO> paged = all.subList(fromIndex, toIndex);

        return PageResult.of(paged, total, page, limit);
    }

    @Override
    public AdminCommentVO getComment(String id, String type) {
        validateType(type);
        if ("forum".equals(type)) {
            ForumComment comment = forumCommentMapper.selectByIdIgnoreDeleted(id);
            if (comment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            AdminCommentReadPort.AuthorSummary author = commentReadPort
                    .findAuthorSummariesByIds(Set.of(comment.getAuthorId()))
                    .get(comment.getAuthorId());
            String postTitle = commentReadPort
                    .findForumPostTitlesByIds(Set.of(comment.getPostId()))
                    .get(comment.getPostId());
            return forumToAdminVO(comment, author, postTitle);
        } else {
            SolutionComment comment = solutionCommentMapper.selectByIdIgnoreDeleted(id);
            if (comment == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            AdminCommentReadPort.AuthorSummary author = commentReadPort
                    .findAuthorSummariesByIds(Set.of(comment.getUserId()))
                    .get(comment.getUserId());
            String solutionTitle = commentReadPort
                    .findSolutionTitlesByIds(Set.of(comment.getSolutionId()))
                    .get(comment.getSolutionId());
            return solutionToAdminVO(comment, author, solutionTitle);
        }
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.FLAG_COMMENT, entityType = AuditActionUtil.ENTITY_COMMENT)
    public AdminCommentVO flagComment(String id, String type, String reason) {
        validateType(type);
        if ("forum".equals(type)) {
            ForumComment comment = getForumCommentEntityOrThrow(id);
            AuditContext.setUserId(comment.getAuthorId());
            AuditContext.setOldValues(Map.of(
                "isFlagged", comment.getIsFlagged(),
                "flaggedReason", comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "",
                "type", "forum"
            ));
            AuditContext.setNewValues(Map.of("isFlagged", true, "flaggedReason", reason != null ? reason : "", "type", "forum"));
            comment.setIsFlagged(true);
            comment.setFlaggedReason(reason);
            comment.setFlaggedAt(LocalDateTime.now());
            forumCommentMapper.updateById(comment);
            log.info("Forum comment flagged: {}", id);
        } else if ("solution".equals(type)) {
            SolutionComment comment = getSolutionCommentEntityOrThrow(id);
            AuditContext.setUserId(comment.getUserId());
            AuditContext.setOldValues(Map.of(
                "isFlagged", comment.getIsFlagged(),
                "flaggedReason", comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "",
                "type", "solution"
            ));
            AuditContext.setNewValues(Map.of("isFlagged", true, "flaggedReason", reason != null ? reason : "", "type", "solution"));
            comment.setIsFlagged(true);
            comment.setFlaggedReason(reason);
            comment.setFlaggedAt(LocalDateTime.now());
            solutionCommentMapper.updateById(comment);
            log.info("Solution comment flagged: {}", id);
        }
        return getComment(id, type);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.UNFLAG_COMMENT, entityType = AuditActionUtil.ENTITY_COMMENT)
    public AdminCommentVO unflagComment(String id, String type) {
        validateType(type);
        if ("forum".equals(type)) {
            ForumComment comment = getForumCommentEntityOrThrow(id);
            AuditContext.setUserId(comment.getAuthorId());
            AuditContext.setOldValues(Map.of(
                "isFlagged", comment.getIsFlagged(),
                "flaggedReason", comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "",
                "type", "forum"
            ));
            AuditContext.setNewValues(Map.of("isFlagged", false, "flaggedReason", "", "type", "forum"));
            // Use LambdaUpdateWrapper with explicit set() so null values are written
            // (entity updateById is silently dropped by FieldStrategy.NOT_NULL).
            forumCommentMapper.update(null, new LambdaUpdateWrapper<ForumComment>()
                .eq(ForumComment::getId, id)
                .set(ForumComment::getIsFlagged, false)
                .set(ForumComment::getFlaggedReason, null)
                .set(ForumComment::getFlaggedAt, null));
            log.info("Forum comment unflagged: {}", id);
        } else if ("solution".equals(type)) {
            SolutionComment comment = getSolutionCommentEntityOrThrow(id);
            AuditContext.setUserId(comment.getUserId());
            AuditContext.setOldValues(Map.of(
                "isFlagged", comment.getIsFlagged(),
                "flaggedReason", comment.getFlaggedReason() != null ? comment.getFlaggedReason() : "",
                "type", "solution"
            ));
            AuditContext.setNewValues(Map.of("isFlagged", false, "flaggedReason", "", "type", "solution"));
            solutionCommentMapper.update(null, new LambdaUpdateWrapper<SolutionComment>()
                .eq(SolutionComment::getId, id)
                .set(SolutionComment::getIsFlagged, false)
                .set(SolutionComment::getFlaggedReason, null)
                .set(SolutionComment::getFlaggedAt, null));
            log.info("Solution comment unflagged: {}", id);
        }
        return getComment(id, type);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.DELETE_COMMENT, entityType = AuditActionUtil.ENTITY_COMMENT)
    public void deleteComment(String id, String type) {
        validateType(type);
        if ("forum".equals(type)) {
            ForumComment comment = getForumCommentEntityOrThrow(id);
            AuditContext.setUserId(comment.getAuthorId());
            AuditContext.setOldValues(Map.of("isDeleted", comment.getIsDeleted(), "type", "forum"));
            AuditContext.setNewValues(Map.of("isDeleted", true, "type", "forum"));
            // Use LambdaUpdateWrapper to bypass MyBatis-Plus FieldStrategy and entity
            // @TableField(updateStrategy=...) uncertainty, so is_deleted=true and other
            // audit fields are reliably persisted in a single statement.
            String currentUserId = SecurityUtil.getCurrentUserId();
            forumCommentMapper.update(null, new LambdaUpdateWrapper<ForumComment>()
                .eq(ForumComment::getId, id)
                .set(ForumComment::getIsDeleted, true)
                .set(ForumComment::getDeletedAt, LocalDateTime.now())
                .set(ForumComment::getDeletedBy, currentUserId));
            log.info("Forum comment deleted: {} by {}", id, currentUserId);
        } else if ("solution".equals(type)) {
            SolutionComment comment = getSolutionCommentEntityOrThrow(id);
            AuditContext.setUserId(comment.getUserId());
            AuditContext.setOldValues(Map.of("isDeleted", comment.getIsDeleted(), "type", "solution"));
            AuditContext.setNewValues(Map.of("isDeleted", true, "type", "solution"));
            String currentUserId = SecurityUtil.getCurrentUserId();
            solutionCommentMapper.update(null, new LambdaUpdateWrapper<SolutionComment>()
                .eq(SolutionComment::getId, id)
                .set(SolutionComment::getIsDeleted, true)
                .set(SolutionComment::getDeletedAt, LocalDateTime.now())
                .set(SolutionComment::getDeletedBy, currentUserId));
            log.info("Solution comment deleted: {} by {}", id, currentUserId);
        }
    }

    @Override
    @Transactional
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
            } catch (RuntimeException e) {
                log.error("Failed to perform action {} on comment {}", request.getAction(), id, e);
                item.setSuccess(false);
                item.setError(e.getMessage());
                response.setFailed(response.getFailed() + 1);
            }

            response.getResults().add(item);
        }

        return response;
    }

    private ForumComment getForumCommentEntityOrThrow(String id) {
        ForumComment comment = forumCommentMapper.selectByIdIgnoreDeleted(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return comment;
    }

    private SolutionComment getSolutionCommentEntityOrThrow(String id) {
        SolutionComment comment = solutionCommentMapper.selectByIdIgnoreDeleted(id);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return comment;
    }

    private AdminCommentVO forumToAdminVO(ForumComment comment,
                                           AdminCommentReadPort.AuthorSummary author,
                                           String postTitle) {
        return new AdminCommentVO(
            comment.getId(),
            comment.getBody(),
            comment.getCreatedAt(),
            comment.getEditedAt() != null ? comment.getEditedAt() : comment.getCreatedAt(),
            comment.getAuthorId(),
            comment.getParentId(),
            "forum",
            comment.getPostId(),
            postTitle,
            author != null ? new AdminCommentVO.AuthorInfo(author.id(), author.username(), author.avatar()) : null,
            comment.getIsFlagged(),
            comment.getFlaggedReason(),
            comment.getFlaggedAt(),
            comment.getIsDeleted(),
            comment.getDeletedAt(),
            comment.getDeletedBy()
        );
    }

    private AdminCommentVO solutionToAdminVO(SolutionComment comment,
                                              AdminCommentReadPort.AuthorSummary author,
                                              String solutionTitle) {
        return new AdminCommentVO(
            comment.getId(),
            comment.getContent(),
            comment.getCreatedAt(),
            comment.getUpdatedAt() != null ? comment.getUpdatedAt() : comment.getCreatedAt(),
            comment.getUserId(),
            comment.getParentId(),
            "solution",
            comment.getSolutionId(),
            solutionTitle,
            author != null ? new AdminCommentVO.AuthorInfo(author.id(), author.username(), author.avatar()) : null,
            comment.getIsFlagged(),
            comment.getFlaggedReason(),
            comment.getFlaggedAt(),
            comment.getIsDeleted(),
            comment.getDeletedAt(),
            comment.getDeletedBy()
        );
    }

    private void validateType(String type) {
        if (!VALID_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid comment type: must be 'forum' or 'solution'");
        }
    }
}
