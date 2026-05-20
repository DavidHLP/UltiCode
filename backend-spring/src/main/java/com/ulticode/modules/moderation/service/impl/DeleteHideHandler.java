package com.ulticode.modules.moderation.service.impl;

import com.ulticode.modules.moderation.entity.ModerationQueue;
import com.ulticode.modules.moderation.entity.enums.ModerationStatus;

import java.time.LocalDateTime;

public final class DeleteHideHandler implements ModerationActionHandler {

    @Override
    public void perform(ActionContext context, ModerationQueue item, String moderatorId, String note, Integer durationDays, LocalDateTime now) {
        context.updateContentFlagStatus(item.getEntityType(), item.getEntityId(), true, note);
        item.setStatus(ModerationStatus.RESOLVED.name());
        item.setResolvedAt(now);
    }
}