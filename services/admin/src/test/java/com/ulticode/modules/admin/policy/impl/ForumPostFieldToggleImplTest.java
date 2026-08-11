package com.ulticode.modules.admin.policy.impl;

import com.ulticode.app.api.command.ForumPostModerationCommand;
import com.ulticode.app.api.dto.ForumPostModerationResultDTO;
import com.ulticode.app.api.service.ForumPostAdministrationService;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.policy.ForumPostFieldToggle.FieldToggle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumPostFieldToggleImplTest {

    @Mock
    private ForumPostAdministrationService forumPostAdministrationService;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private ForumPostFieldToggleImpl policy;

    @BeforeEach
    void setUp() {
        policy = new ForumPostFieldToggleImpl(
                forumPostAdministrationService, auditRecorder, currentUserProvider);
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")).thenReturn(true);
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);
        when(forumPostAdministrationService.moderate(any())).thenAnswer(invocation -> {
            ForumPostModerationCommand command = invocation.getArgument(0);
            return RpcResult.success(new ForumPostModerationResultDTO(
                    command.postId(), command.action(), "u1", false, null), "trace-1");
        });
    }

    @Test
    @DisplayName("toggle(PIN) delegates to App command and writes audit")
    void toggle_pin_writesAuditAndPersists() {
        policy.toggle("p1", FieldToggle.PIN);

        verify(forumPostAdministrationService).moderate(any(ForumPostModerationCommand.class));
        verify(auditRecorder).recordForUser(
                org.mockito.ArgumentMatchers.eq("PIN_POST"),
                org.mockito.ArgumentMatchers.eq("FORUM_POST"),
                org.mockito.ArgumentMatchers.eq("p1"),
                org.mockito.ArgumentMatchers.eq("u1"),
                org.mockito.ArgumentMatchers.eq(Map.of("isPinned", false)),
                org.mockito.ArgumentMatchers.eq(Map.of("isPinned", true)));
    }

    @Test
    @DisplayName("toggle(LOCK) delegates to App command and writes audit")
    void toggle_lock_writesAuditAndPersists() {
        policy.toggle("p1", FieldToggle.LOCK);

        verify(forumPostAdministrationService).moderate(any(ForumPostModerationCommand.class));
        verify(auditRecorder).recordForUser(
                org.mockito.ArgumentMatchers.eq("LOCK_POST"),
                org.mockito.ArgumentMatchers.eq("FORUM_POST"),
                org.mockito.ArgumentMatchers.eq("p1"),
                org.mockito.ArgumentMatchers.eq("u1"),
                org.mockito.ArgumentMatchers.eq(Map.of("isLocked", false)),
                org.mockito.ArgumentMatchers.eq(Map.of("isLocked", true)));
    }
}
