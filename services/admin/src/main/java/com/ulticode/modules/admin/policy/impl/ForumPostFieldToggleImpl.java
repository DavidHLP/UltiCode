package com.ulticode.modules.admin.policy.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.ForumPostModerationCommand;
import com.ulticode.app.api.dto.ForumPostModerationResultDTO;
import com.ulticode.app.api.service.ForumPostAdministrationService;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Default {@link ForumPostFieldToggle} implementation.
 *
 * <p>Post mutations cross the typed App-owned command boundary; the App
 * provider owns authorization, receipt deduplication, and the row update.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForumPostFieldToggleImpl implements ForumPostFieldToggle {

    private static final String ENTITY_FORUM_POST = "FORUM_POST";

    private final ForumPostAdministrationService forumPostAdministrationService;
    private final AuditRecorder auditRecorder;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public void toggle(String postId, FieldToggle fieldToggle) {
        ForumPostModerationCommand.Action action = switch (fieldToggle) {
            case PIN -> ForumPostModerationCommand.Action.PIN;
            case UNPIN -> ForumPostModerationCommand.Action.UNPIN;
            case LOCK -> ForumPostModerationCommand.Action.LOCK;
            case UNLOCK -> ForumPostModerationCommand.Action.UNLOCK;
        };
        ForumPostModerationResultDTO result = moderate(postId, action);

        auditRecorder.recordForUser(
                fieldToggle.auditAction(),
                ENTITY_FORUM_POST,
                postId,
                result.authorUserId(),
                Map.of(fieldToggle.fieldName(), result.previousState()),
                Map.of(fieldToggle.fieldName(), fieldToggle.newValue()));
        log.info("Post {}: {}", fieldToggle.logVerb(), postId);
    }

    private ForumPostModerationResultDTO moderate(
            String postId, ForumPostModerationCommand.Action action) {
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED,
                    "Authenticated admin actor is required");
        }
        if (!currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            throw new BusinessException(AdminErrorCode.FORBIDDEN);
        }
        RpcResult<ForumPostModerationResultDTO> response =
                forumPostAdministrationService.moderate(new ForumPostModerationCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new ActorDelegation(
                                currentUserProvider.hasRole("SUPER_ADMIN")
                                        ? "SUPER_ADMIN" : "ADMIN",
                                actorId, actorId, "forum post moderation"),
                        currentTrace(),
                        postId, action, null));
        if (!response.success() || response.data() == null) {
            throw mapError(response);
        }
        return response.data();
    }

    private static TraceMetadata currentTrace() {
        String reqId = TraceIdUtil.current();
        if (reqId == null || reqId.isBlank()) {
            reqId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(reqId, null, null, null);
    }

    private static BusinessException mapError(RpcResult<?> response) {
        if (response.error() == null) {
            return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "RPC failed without error payload");
        }
        return switch (response.error().code()) {
            case 40000 -> new BusinessException(AdminErrorCode.BAD_REQUEST, response.error().message());
            case 40100 -> new BusinessException(AdminErrorCode.UNAUTHORIZED, response.error().message());
            case 40300 -> new BusinessException(AdminErrorCode.FORBIDDEN, response.error().message());
            case 40401 -> new BusinessException(AdminErrorCode.NOT_FOUND, response.error().message());
            case 40903 -> new BusinessException(AdminErrorCode.CONFLICT, response.error().message());
            default -> new BusinessException(AdminErrorCode.UNKNOWN_ERROR, response.error().message());
        };
    }
}
