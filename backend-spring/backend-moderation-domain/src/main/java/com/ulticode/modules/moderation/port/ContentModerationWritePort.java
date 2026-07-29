package com.ulticode.modules.moderation.port;

import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;

/**
 * App-owned write port interface for content moderation administration.
 */
public interface ContentModerationWritePort {

    ModerationApplyResultDTO apply(ApplyModerationCommand command);
}
