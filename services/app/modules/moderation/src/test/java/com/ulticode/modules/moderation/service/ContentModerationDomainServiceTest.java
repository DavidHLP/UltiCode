package com.ulticode.modules.moderation.service;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.moderation.port.ContentModerationWritePort;
import com.ulticode.modules.moderation.service.impl.ContentModerationDomainServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentModerationDomainService")
class ContentModerationDomainServiceTest {

    @Mock
    private ContentModerationWritePort writePort;

    private ContentModerationDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = new ContentModerationDomainServiceImpl(writePort);
    }

    private static ActorDelegation actor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }

    @Test
    @DisplayName("apply delegates to writePort")
    void apply() {
        var cmd = new ApplyModerationCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(), actor(), TraceMetadata.EMPTY,
                "case-1", "post-1", "forum_post", ModerationAction.DELETE, "Spam");
        var expected = new ModerationApplyResultDTO(
                "case-1", "post-1", ModerationAction.DELETE, ContentLifecycleState.DELETED);

        when(writePort.apply(cmd)).thenReturn(expected);

        var result = domainService.apply(cmd);

        assertThat(result).isEqualTo(expected);
        verify(writePort).apply(cmd);
    }
}
