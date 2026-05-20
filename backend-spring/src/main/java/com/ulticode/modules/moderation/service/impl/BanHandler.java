package com.ulticode.modules.moderation.service.impl;

import com.ulticode.modules.moderation.entity.ModerationQueue;
import com.ulticode.modules.moderation.entity.enums.ModerationStatus;

import java.time.LocalDateTime;

public final class BanHandler implements ModerationActionHandler {

    private final boolean isPermanent;

    public BanHandler(boolean isPermanent) {
        this.isPermanent = isPermanent;
    }

    @Override
    public void perform(ActionContext context, ModerationQueue item, String moderatorId, String note, Integer durationDays, LocalDateTime now) {
        if (isPermanent) {
            context.createUserBan(item.getAuthorId(), context.queueId(), note, item.getPrimaryCategory(), moderatorId, context.actionId(), null, true);
        } else {
            context.createUserBan(item.getAuthorId(), context.queueId(), note, item.getPrimaryCategory(), moderatorId, context.actionId(), durationDays, false);
        }
        item.setStatus(ModerationStatus.RESOLVED.name());
        item.setResolvedAt(now);
    }
}