package com.ulticode.modules.admin.policy.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.ForumPostModerationCommand;
import com.ulticode.app.api.dto.ForumPostModerationResultDTO;
import com.ulticode.app.api.service.ForumPostAdministrationService;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.policy.ForumFlagPolicy;
import com.ulticode.modules.admin.write.AdminOwnerErrorMapper;
import com.ulticode.modules.admin.write.AdminWriteEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Default {@link ForumFlagPolicy} implementation.
 *
 * <p>Post mutations cross the typed App-owned command boundary; the App
 * provider owns authorization, receipt deduplication, and the row update.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForumFlagPolicyImpl implements ForumFlagPolicy {

    private static final String ENTITY_FORUM_POST = "FORUM_POST";
    private static final String ACTION_FLAG_POST = "FLAG_POST";
    private static final String ACTION_UNFLAG_POST = "UNFLAG_POST";

    private final ForumPostAdministrationService forumPostAdministrationService;
    private final AuditRecorder auditRecorder;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public void flag(String postId, String reason) {
        ForumPostModerationResultDTO result = moderate(
                postId, ForumPostModerationCommand.Action.FLAG, reason);
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("isFlagged", result.previousState());
        oldValues.put("flaggedReason", result.previousReason());
        auditRecorder.recordForUser(
                ACTION_FLAG_POST,
                ENTITY_FORUM_POST,
                postId,
                result.authorUserId(),
                oldValues,
                Map.of(
                        "isFlagged", true,
                        "flaggedReason", reason != null ? reason : ""));
        log.info("Post flagged: {} reason: {}", postId, reason);
    }

    @Override
    public void unflag(String postId) {
        ForumPostModerationResultDTO result = moderate(
                postId, ForumPostModerationCommand.Action.UNFLAG, null);
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("isFlagged", result.previousState());
        oldValues.put("flaggedReason", result.previousReason());
        auditRecorder.recordForUser(
                ACTION_UNFLAG_POST,
                ENTITY_FORUM_POST,
                postId,
                result.authorUserId(),
                oldValues,
                Map.of(
                        "isFlagged", false,
                        "flaggedReason", ""));
        log.info("Post unflagged: {}", postId);
    }

    private ForumPostModerationResultDTO moderate(
        String postId, ForumPostModerationCommand.Action action, String reason) {
        String actorId = currentUserProvider.getCurrentUserId();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "forum-post-moderation", null, actorId, currentUserProvider,
                "forum post moderation");
        if (!currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            throw new BusinessException(AdminErrorCode.FORBIDDEN);
        }
        RpcResult<ForumPostModerationResultDTO> response =
                forumPostAdministrationService.moderate(new ForumPostModerationCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                        postId, action, reason));
        if (response == null || !response.success() || response.data() == null) {
            throw AdminOwnerErrorMapper.mapOwnerError(
                    AdminOwnerErrorMapper.Owner.FORUM_POST, response);
        }
        return response.data();
    }

}
