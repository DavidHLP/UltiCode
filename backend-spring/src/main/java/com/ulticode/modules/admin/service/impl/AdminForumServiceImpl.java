package com.ulticode.modules.admin.service.impl;

import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.policy.ForumFlagPolicy;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import com.ulticode.modules.admin.service.AdminForumService;
import com.ulticode.modules.admin.service.AuditService;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Write-only implementation of {@link AdminForumService} after the ADR-0011
 * Stage 2 extraction, with the C6 forum-toggle policy collapse applied.
 *
 * <p>All read paths (paginated post list, single-detail post, community list)
 * moved to {@link com.ulticode.modules.admin.projection.AdminForumProjection}
 * / {@code DefaultAdminForumProjection}. The six copy-pasted toggle methods
 * (pin / unpin / lock / unlock / flag / unflag) became thin one-line
 * delegates over {@link ForumPostFieldToggle} and {@link ForumFlagPolicy}.
 * Cross-module entity imports ({@code User}, {@code ForumCommunity},
 * {@code ForumCommentMapper}, {@code EdgeOperationMapper}) and the inline
 * {@code toAdminVO} overloads left this service with the write state machine
 * (delete / bulk action / audit-history delegation) plus the two policy
 * seams.
 *
 * <p>The controller depends on the projection for reads and on this service
 * for writes; write methods return {@code void} / {@link BulkActionResult},
 * so no post-write VO composition is needed.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminForumServiceImpl implements AdminForumService {

    private final ForumPostMapper forumPostMapper;
    private final AuditService auditService;
    private final AuditHelper auditHelper;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;
    private final ForumPostFieldToggle forumPostFieldToggle;
    private final ForumFlagPolicy forumFlagPolicy;

    @Override
    public void pinPost(String id) {
        forumPostFieldToggle.toggle(id, FieldToggle.PIN);
    }

    @Override
    public void unpinPost(String id) {
        forumPostFieldToggle.toggle(id, FieldToggle.UNPIN);
    }

    @Override
    public void lockPost(String id) {
        forumPostFieldToggle.toggle(id, FieldToggle.LOCK);
    }

    @Override
    public void unlockPost(String id) {
        forumPostFieldToggle.toggle(id, FieldToggle.UNLOCK);
    }

    @Override
    public void deletePost(String id) {
        ForumPost post = getPostEntityOrThrow(id);
        // The is_deleted column carries @TableLogic, so MyBatis-Plus's updateById
        // silently drops the field — soft-delete must go through the dedicated
        // mapper method (which uses SQL NOW() and avoids the JSR-310 round-trip
        // through JacksonTypeHandler).
        String performerId = currentUserProvider.getCurrentUserId();
        if (performerId == null) {
            performerId = "system";
        }
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("isDeleted", post.getIsDeleted() != null ? post.getIsDeleted() : false);
        oldValues.put("deletedAt", post.getDeletedAt());
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("isDeleted", true);
        newValues.put("deletedAt", LocalDateTime.now(clock));
        newValues.put("deletedBy", performerId);
        // Audit FIRST: if audit fails we roll back the soft delete (audit
        // integrity beats delete throughput).
        auditHelper.logForUser(
            AuditVocabulary.DELETE_FORUM_POST,
            AuditVocabulary.ENTITY_FORUM_POST,
            id,
            post.getUserId(),
            oldValues,
            newValues
        );
        int affected = forumPostMapper.softDelete(id, performerId);
        if (affected == 0) {
            // Row was concurrently modified/deleted between selectById and softDelete.
            // Throw to roll back the audit entry as well — a dangling audit row
            // pointing at a non-existent soft delete is worse than a clean error.
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        log.info("Post soft-deleted: {} by {}", id, performerId);
    }

    @Override
    public void flagPost(String id, String reason) {
        forumFlagPolicy.flag(id, reason);
    }

    @Override
    public void unflagPost(String id) {
        forumFlagPolicy.unflag(id);
    }

    @Override
    public BulkActionResult bulkAction(List<String> ids, String action) {
        BulkActionResult response = new BulkActionResult();
        response.setTotal(ids.size());
        response.setResults(new ArrayList<>());
        response.setSuccessful(0);
        response.setFailed(0);

        for (String id : ids) {
            BulkActionResult.BulkActionItem item = new BulkActionResult.BulkActionItem();
            item.setId(id);

            try {
                switch (action) {
                    case "delete" -> deletePost(id);
                    case "pin" -> pinPost(id);
                    case "unpin" -> unpinPost(id);
                    case "lock" -> lockPost(id);
                    case "unlock" -> unlockPost(id);
                    case "unflag" -> unflagPost(id);
                    default -> throw new IllegalArgumentException("Unknown action: " + action);
                }
                item.setSuccess(true);
                response.setSuccessful(response.getSuccessful() + 1);
            } catch (RuntimeException e) {
                log.error("Failed to perform action {} on post {}", action, id, e);
                item.setSuccess(false);
                item.setError(e.getMessage());
                response.setFailed(response.getFailed() + 1);
            }

            response.getResults().add(item);
        }

        return response;
    }

    @Override
    public List<AuditLogVO> getPostAuditHistory(String id) {
        AuditLogQueryDTO query = new AuditLogQueryDTO();
        query.setEntityType("FORUM_POST");
        query.setEntityId(id);
        query.setPage(1);
        query.setLimit(100);
        return auditService.getAuditLogs(query).getItems();
    }

    /**
     * Get ForumPost entity or throw exception.
     */
    private ForumPost getPostEntityOrThrow(String id) {
        ForumPost post = forumPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return post;
    }
}
