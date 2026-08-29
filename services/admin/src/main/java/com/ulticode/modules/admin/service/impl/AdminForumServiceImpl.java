package com.ulticode.modules.admin.service.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.dto.AdminForumPostRowDTO;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.service.AdminForumReadPort;
import com.ulticode.app.api.service.ContentModerationService;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.auth.AdminActors;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.bulk.AdminBulkExecutor;
import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.dto.AuditLogVO;
import com.ulticode.modules.admin.dto.BulkActionResult;
import com.ulticode.modules.admin.policy.ForumFlagPolicy;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import com.ulticode.modules.admin.service.AdminForumService;
import com.ulticode.modules.admin.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ulticode.common.rpc.RpcPolicy;

/**
 * Write-only implementation of {@link AdminForumService} after the ADR-0011
 * Stage 2 extraction, the C6 forum-toggle policy collapse, and ADMIN-007.
 *
 * <p>The six copy-pasted toggle methods (pin / unpin / lock / unlock /
 * flag / unflag) are thin one-line delegates over
 * {@link ForumPostFieldToggle} and {@link ForumFlagPolicy}. The soft-delete
 * write now routes through the App-owned {@link ContentModerationService}
 * Dubbo provider (with full command / idempotency / actor / trace
 * metadata) instead of {@code ForumPostMapper} — no forum entity or
 * mapper is imported. The audit record is written FIRST (audit integrity
 * beats delete throughput, preserving the pre-migration invariant); a
 * failed RPC surfaces as an {@link AdminErrorCode} exception. The audit
 * row and the remote delete cannot share a transaction across the Dubbo
 * boundary, so no local transaction wraps the call.
 *
 * <p>Read paths (paginated post list, single-detail post, community list)
 * live on {@link AdminForumProjection} / {@code DefaultAdminForumProjection}.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminForumServiceImpl implements AdminForumService {

    private final AuditService auditService;
    private final AuditRecorder auditRecorder;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;
    private final ForumPostFieldToggle forumPostFieldToggle;
    private final ForumFlagPolicy forumFlagPolicy;
    private final AdminBulkExecutor bulkExecutor;
    private final AdminForumReadPort adminForumReadPort;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ContentModerationService contentModerationService;

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
        AdminForumPostRowDTO post = adminForumReadPort.getPost(id);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new BusinessException(AdminErrorCode.NOT_FOUND);
        }
        String performerId = currentUserProvider.getCurrentUserId();
        if (performerId == null || performerId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("isDeleted", false);
        oldValues.put("deletedAt", post.getDeletedAt());
        Map<String, Object> newValues = new HashMap<>();
        newValues.put("isDeleted", true);
        newValues.put("deletedAt", LocalDateTime.now(clock));
        newValues.put("deletedBy", performerId);
        String actorId = performerId;
        String caseId = UUID.randomUUID().toString();
        RpcResult<ModerationApplyResultDTO> result = contentModerationService.apply(
                new ApplyModerationCommand(
                        UUID.randomUUID().toString(), IdMetadata.mint(),
                        new ActorDelegation(
                                AdminActors.typeOf(currentUserProvider),
                                actorId, actorId, "admin forum delete"),
                        currentTrace(),
                        caseId, id, "forum_post", ModerationAction.DELETE,
                        "admin forum post soft-delete"));
        if (result == null || !result.success()) {
            throw mapError(result);
        }
        auditRecorder.recordForUser(
                AuditVocabulary.DELETE_FORUM_POST,
                AuditVocabulary.ENTITY_FORUM_POST,
                id,
                post.getUserId(),
                oldValues,
                newValues);
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
        AdminBulkExecutor.Run run = bulkExecutor.run(ids, action, id -> {
            switch (action) {
                case "delete" -> deletePost(id);
                case "pin" -> pinPost(id);
                case "unpin" -> unpinPost(id);
                case "lock" -> lockPost(id);
                case "unlock" -> unlockPost(id);
                case "unflag" -> unflagPost(id);
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            }
        }, id -> true);

        BulkActionResult response = new BulkActionResult();
        response.setTotal(run.total());
        response.setSuccessful(run.successful());
        response.setFailed(run.failed());
        response.setResults(new ArrayList<>(run.items().size()));
        for (AdminBulkExecutor.ItemOutcome outcome : run.items()) {
            response.getResults().add(new BulkActionResult.BulkActionItem(
                outcome.id(), outcome.isSuccess(), outcome.errorOrNull()));
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

    private static TraceMetadata currentTrace() {
        String reqId = TraceIdUtil.current();
        if (reqId == null || reqId.isBlank()) {
            reqId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(reqId, null, null, null);
    }

    private static BusinessException mapError(RpcResult<?> result) {
        if (result == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "RPC result is null (transport failure)");
        }
        var err = result.error();
        if (err == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "RPC failed without error payload");
        }
        return switch (err.code()) {
            case 40000 -> new BusinessException(AdminErrorCode.BAD_REQUEST, err.message());
            case 40100 -> new BusinessException(AdminErrorCode.UNAUTHORIZED, err.message());
            case 40300 -> new BusinessException(AdminErrorCode.FORBIDDEN, err.message());
            case 40401 -> new BusinessException(AdminErrorCode.NOT_FOUND, err.message());
            case 40902 -> new BusinessException(AdminErrorCode.CONFLICT, err.message());
            default -> new BusinessException(AdminErrorCode.UNKNOWN_ERROR, err.message());
        };
    }
}
