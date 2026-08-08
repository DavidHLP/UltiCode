package com.ulticode.modules.moderation.service.impl;

import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.modules.moderation.port.ContentModerationWritePort;
import com.ulticode.modules.moderation.service.ContentModerationDomainService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContentModerationDomainServiceImpl implements ContentModerationDomainService {

    private final ContentModerationWritePort writePort;

    public ContentModerationDomainServiceImpl(ContentModerationWritePort writePort) {
        this.writePort = writePort;
    }

    @Override
    public ModerationApplyResultDTO apply(ApplyModerationCommand command) {
        log.info("ContentModerationDomainServiceImpl.apply commandId={} caseId={} contentType={} action={}",
                command.commandId(), command.moderationCaseId(),
                command.contentType(), command.action());
        return writePort.apply(command);
    }
}
