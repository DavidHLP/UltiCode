package com.ulticode.modules.moderation.service.impl;

import com.ulticode.modules.moderation.entity.ModerationQueue;
import com.ulticode.modules.moderation.entity.enums.ModerationActionType;

import java.time.LocalDateTime;

public sealed interface ModerationActionHandler
    permits DeleteHideHandler,
        RestoreDismissHandler,
        WarnHandler,
        BanHandler,
        AppealHandler {

    void perform(ActionContext context, ModerationQueue item, String moderatorId, String note, Integer durationDays, LocalDateTime now);

    static ModerationActionHandler from(ModerationActionType action) {
        return switch (action) {
            case DELETED, HIDDEN -> new DeleteHideHandler();
            case RESTORED, DISMISSED, RESOLVED -> new RestoreDismissHandler();
            case WARNED -> new WarnHandler();
            case TEMP_BANNED -> new BanHandler(false);
            case PERM_BANNED -> new BanHandler(true);
            case APPEAL_PENDING -> new AppealHandler(ModerationActionType.APPEAL_PENDING);
            case APPEAL_APPROVED -> new AppealHandler(ModerationActionType.APPEAL_APPROVED);
            case APPEAL_REJECTED -> new AppealHandler(ModerationActionType.APPEAL_REJECTED);
        };
    }

    record ActionContext(
        ModerationServiceImpl service,
        String queueId,
        String actionId
    ) {
        void updateContentFlagStatus(String entityType, String entityId, boolean isFlagged, String reason) {
            service.updateContentFlagStatus(entityType, entityId, isFlagged, reason);
        }

        void createUserWarning(String userId, String queueId, String reason, String category, String actionId) {
            service.createUserWarning(userId, queueId, reason, category, actionId);
        }

        void createUserBan(String userId, String queueId, String reason, String category, String bannedById, String actionId, Integer durationDays, boolean isPermanent) {
            service.createUserBan(userId, queueId, reason, category, bannedById, actionId, durationDays, isPermanent);
        }
    }
}