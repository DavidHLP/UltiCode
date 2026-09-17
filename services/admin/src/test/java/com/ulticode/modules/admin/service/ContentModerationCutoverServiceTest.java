package com.ulticode.modules.admin.service;

import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.service.ContentModerationService;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.port.adapter.OwnerCutoverGate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContentModerationCutoverService")
class ContentModerationCutoverServiceTest {

    @Mock private AdminForumService forumService;
    @Mock private AdminSolutionService solutionService;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ContentModerationService contentModerationProvider;

    @Test
    @DisplayName("LOCAL decision dispatches to the admin services")
    void localDecisionDispatchesLocally() {
        ContentModerationCutoverService service = service(false);

        service.moderateForumPost("post-1", ModerationAction.DELETE);
        service.moderateSolution("sol-1", ModerationAction.DELETE);

        verify(forumService).deletePost("post-1");
        verify(solutionService).deleteSolution("sol-1");
        verify(contentModerationProvider, never()).apply(any());
    }

    @Test
    @DisplayName("REMOTE decision routes through the provider")
    void remoteDecisionRoutesThroughProvider() {
        ContentModerationCutoverService service = service(true);
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(contentModerationProvider.apply(any())).thenReturn(RpcResult.success(
                new ModerationApplyResultDTO(
                        "case-1", "post-1", ModerationAction.DELETE, ContentLifecycleState.DELETED),
                "trace-1"));

        service.moderateForumPost("post-1", ModerationAction.DELETE);

        verify(contentModerationProvider).apply(any());
        verify(forumService, never()).deletePost(any());
    }

    private ContentModerationCutoverService service(boolean remoteEnabled) {
        OwnerCutoverGate gate = new OwnerCutoverGate(
                "moderation", null, false, remoteEnabled,
                OwnerCutoverGate.Policy.DELEGATE_LOCAL);
        ContentModerationCutoverService service = new ContentModerationCutoverService(
                forumService, solutionService, currentUserProvider, gate);
        ReflectionTestUtils.setField(service, "dubboProvider", contentModerationProvider);
        return service;
    }
}
