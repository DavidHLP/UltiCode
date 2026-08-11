package com.ulticode.modules.admin.policy.impl;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.ForumPostModerationCommand;
import com.ulticode.app.api.dto.ForumPostModerationResultDTO;
import com.ulticode.app.api.service.ForumPostAdministrationService;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumFlagPolicyImplTest {

    @Mock
    private ForumPostAdministrationService forumPostAdministrationService;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private ForumFlagPolicyImpl policy;

    @BeforeEach
    void setUp() {
        policy = new ForumFlagPolicyImpl(
                forumPostAdministrationService, auditRecorder, currentUserProvider);
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
    }

    private void stubAdminMutation() {
        when(currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")).thenReturn(true);
        when(currentUserProvider.hasRole("SUPER_ADMIN")).thenReturn(false);
        when(forumPostAdministrationService.moderate(any())).thenAnswer(invocation -> {
            ForumPostModerationCommand command = invocation.getArgument(0);
            ForumPostModerationResultDTO result = command.action() == ForumPostModerationCommand.Action.FLAG
                    ? new ForumPostModerationResultDTO("p1", command.action(), "u1", false, null)
                    : new ForumPostModerationResultDTO("p1", command.action(), "u1", true, "spam");
            return RpcResult.success(result, "trace-1");
        });
    }


    @Test
    @DisplayName("flag delegates through App contract and preserves nullable old audit values")
    void flag_writesAuditAndDelegates() {
        stubAdminMutation();
        policy.flag("p1", "Spam");

        verify(forumPostAdministrationService).moderate(any(ForumPostModerationCommand.class));
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("isFlagged", false);
        oldValues.put("flaggedReason", null);
        verify(auditRecorder).recordForUser(
                org.mockito.ArgumentMatchers.eq("FLAG_POST"),
                org.mockito.ArgumentMatchers.eq("FORUM_POST"),
                org.mockito.ArgumentMatchers.eq("p1"),
                org.mockito.ArgumentMatchers.eq("u1"),
                org.mockito.ArgumentMatchers.eq(oldValues),
                org.mockito.ArgumentMatchers.eq(Map.of("isFlagged", true, "flaggedReason", "Spam")));
    }

    @Test
    @DisplayName("unflag delegates through App contract and writes audit")
    void unflag_clearsFieldsAndWritesAudit() {
        stubAdminMutation();
        policy.unflag("p1");

        verify(forumPostAdministrationService).moderate(any(ForumPostModerationCommand.class));
        Map<String, Object> oldValues = new HashMap<>();
        oldValues.put("isFlagged", true);
        oldValues.put("flaggedReason", "spam");
        verify(auditRecorder).recordForUser(
                org.mockito.ArgumentMatchers.eq("UNFLAG_POST"),
                org.mockito.ArgumentMatchers.eq("FORUM_POST"),
                org.mockito.ArgumentMatchers.eq("p1"),
                org.mockito.ArgumentMatchers.eq("u1"),
                org.mockito.ArgumentMatchers.eq(oldValues),
                org.mockito.ArgumentMatchers.eq(Map.of("isFlagged", false, "flaggedReason", "")));
    }

    @Test
    void flag_rejectsMissingAdminIdentity() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);

        assertThatThrownBy(() -> policy.flag("p1", "Spam"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .extracting(error -> ((com.ulticode.common.exception.BusinessException) error).getErrorCode())
                .isEqualTo(AdminErrorCode.UNAUTHORIZED);
    }
}
