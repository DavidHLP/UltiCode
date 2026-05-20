package com.ulticode.modules.moderation.service.impl;

import com.ulticode.modules.moderation.entity.ModerationQueue;
import com.ulticode.modules.moderation.entity.enums.ModerationStatus;

import java.time.LocalDateTime;

public final class WarnHandler implements ModerationActionHandler {

    @Override
    public void perform(ActionContext context, ModerationQueue item, String moderatorId, String note, Integer durationDays, LocalDateTime now) {
        context.createUserWarning(item.getAuthorId(), context.queueId(), note, item.getPrimaryCategory(), context.actionId());
        item.setStatus(ModerationStatus.RESOLVED.name());
        item.setResolvedAt(now);
    }
}