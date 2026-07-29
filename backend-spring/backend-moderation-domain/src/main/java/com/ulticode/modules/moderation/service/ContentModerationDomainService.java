package com.ulticode.modules.moderation.service;

import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;

public interface ContentModerationDomainService {

    ModerationApplyResultDTO apply(ApplyModerationCommand command);
}
