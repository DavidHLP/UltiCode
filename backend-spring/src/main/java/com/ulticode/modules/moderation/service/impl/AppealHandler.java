package com.ulticode.modules.moderation.service.impl;

import com.ulticode.modules.moderation.entity.ModerationQueue;
import com.ulticode.modules.moderation.entity.enums.ModerationActionType;
import com.ulticode.modules.moderation.entity.enums.ModerationStatus;

import java.time.LocalDateTime;

public final class AppealHandler implements ModerationActionHandler {

    private final ModerationActionType actionType;

    public AppealHandler(ModerationActionType actionType) {
        this.actionType = actionType;
    }

    @Override
    public void perform(ActionContext context, ModerationQueue item, String moderatorId, String note, Integer durationDays, LocalDateTime now) {
        switch (actionType) {
            case APPEAL_PENDING -> item.setStatus(ModerationStatus.APPEAL_PENDING.name());
            case APPEAL_APPROVED -> {
                context.updateContentFlagStatus(item.getEntityType(), item.getEntityId(), false, null);
                item.setStatus(ModerationStatus.RESOLVED.name());
                item.setResolvedAt(now);
            }
            case APPEAL_REJECTED -> {
                item.setStatus(ModerationStatus.RESOLVED.name());
                item.setResolvedAt(now);
            }
            default -> throw new IllegalArgumentException("Not an appeal action: " + actionType);
        }
    }
}