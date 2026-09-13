package com.ulticode.modules.admin.policy.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.ForumPostModerationCommand;
import com.ulticode.app.api.dto.ForumPostModerationResultDTO;
import com.ulticode.app.api.service.ForumPostAdministrationService;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import com.ulticode.modules.admin.write.AdminOwnerErrorMapper;
import com.ulticode.modules.admin.write.AdminWriteEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

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
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "forum-post-moderation", null, actorId, currentUserProvider,
                "forum post moderation");
        if (!currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            throw new BusinessException(AdminErrorCode.FORBIDDEN);
        }
        RpcResult<ForumPostModerationResultDTO> response =
                forumPostAdministrationService.moderate(new ForumPostModerationCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(), envelope.trace(),
                        postId, action, null));
        if (response == null || !response.success() || response.data() == null) {
            throw AdminOwnerErrorMapper.mapOwnerError(
                    AdminOwnerErrorMapper.Owner.FORUM_POST, response);
        }
        return response.data();
    }

}
