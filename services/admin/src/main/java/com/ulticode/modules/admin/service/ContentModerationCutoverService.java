package com.ulticode.modules.admin.service;

import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.service.ContentModerationService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ulticode.modules.admin.write.AdminOwnerErrorMapper;
import com.ulticode.modules.admin.write.AdminWriteEnvelope;
import java.util.UUID;
import com.ulticode.common.rpc.RpcPolicy;

/**
 * P4-CUTOVER-004: feature-flagged routing adapter for content moderation.
 *
 * <p>When {@code app.features.moderation-dubbo-cutover=false} (default),
 * delegates directly to the local admin service (AdminForumService or
 * AdminSolutionService). When the flag is {@code true}, moderation writes
 * go through the Dubbo {@link ContentModerationService} Provider.
 *
 * <p>Mirrors {@code NotificationCutoverService} / {@link ContestCutoverService}
 * in pattern. Only DELETE action is currently routed (matching the admin
 * services' soft-delete methods); HIDE/RESTORE/UNDELETE are deferred until
 * those operations exist on the admin services.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentModerationCutoverService {

    private final AdminForumService forumService;
    private final AdminSolutionService solutionService;
    private final CurrentUserProvider currentUserProvider;

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private ContentModerationService dubboProvider;

    @Value("${app.features.moderation-dubbo-cutover:false}")
    private boolean dubboEnabled;

    /**
     * Apply a moderation action to forum content.
     */
    public void moderateForumPost(String id, ModerationAction action) {
        moderate(id, "forum_post", action);
    }

    /**
     * Apply a moderation action to solution content.
     */
    public void moderateSolution(String id, ModerationAction action) {
        moderate(id, "solution", action);
    }

    private void moderate(String contentId, String contentType, ModerationAction action) {
        if (!dubboEnabled) {
            // Local path: dispatch to the admin service directly
            dispatchLocal(contentId, contentType, action);
            return;
        }
        // Dubbo path: route through the Provider
        String actorId = currentUserProvider.getCurrentUserId();
        String caseId = UUID.randomUUID().toString();
        AdminWriteEnvelope envelope = AdminWriteEnvelope.envelope(
                "content-moderation", null, actorId, currentUserProvider,
                "cutover moderation");
        RpcResult<ModerationApplyResultDTO> result = dubboProvider.apply(
                new ApplyModerationCommand(
                        envelope.commandId(), envelope.idempotency(), envelope.actor(),
                        envelope.trace(),
                        caseId, contentId, contentType, action,
                        "admin moderation cutover"));
        if (result == null || !result.success()) {
            throw AdminOwnerErrorMapper.mapOwnerError(
                    AdminOwnerErrorMapper.Owner.CONTENT_MODERATION, result);
        }
    }

    private void dispatchLocal(String contentId, String contentType, ModerationAction action) {
        if (action != ModerationAction.DELETE) {
            throw new BusinessException(AdminErrorCode.BAD_REQUEST,
                    "Unsupported moderation action: " + action);
        }
        switch (contentType) {
            case "forum_post", "forum" -> forumService.deletePost(contentId);
            case "solution" -> solutionService.deleteSolution(contentId);
            default -> throw new BusinessException(AdminErrorCode.BAD_REQUEST,
                    "Unknown contentType: " + contentType);
        }
    }

}
