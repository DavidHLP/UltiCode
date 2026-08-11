package com.ulticode.modules.admin.service.handler;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.ForumTagMutationCommand;
import com.ulticode.app.api.dto.ForumTagDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ForumTagAdministrationService;
import com.ulticode.app.api.service.ForumTagReadPort;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.tag.CreateTagDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumTagHandlerTest {

    @Mock
    private ForumTagReadPort forumTagReadPort;

    @Mock
    private ForumTagAdministrationService forumTagAdministrationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private ForumTagHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ForumTagHandler(
                forumTagReadPort,
                forumTagAdministrationService,
                currentUserProvider);
    }

    @Test
    void createRoutesTypedCommandWithAdminActor() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(forumTagAdministrationService.mutate(any()))
                .thenReturn(RpcResult.success(new ForumTagDTO(
                        "tag-1", "Java", "java", "language", "#fff", 0, null), "trace-1"));
        CreateTagDTO dto = new CreateTagDTO();
        dto.setName("Java");
        dto.setDescription("language");
        dto.setColor("#fff");

        var result = handler.create(dto, "java");

        assertThat(result.getId()).isEqualTo("tag-1");
        assertThat(result.getType()).isEqualTo("FORUM");
        ArgumentCaptor<ForumTagMutationCommand> captor =
                ArgumentCaptor.forClass(ForumTagMutationCommand.class);
        verify(forumTagAdministrationService).mutate(captor.capture());
        ForumTagMutationCommand command = captor.getValue();
        assertThat(command.action()).isEqualTo(ForumTagMutationCommand.Action.CREATE);
        assertThat(command.name()).isEqualTo("Java");
        assertThat(command.slug()).isEqualTo("java");
        assertThat(command.actor().actorId()).isEqualTo("admin-1");
        assertThat(command.idempotency().hasKey()).isTrue();
    }

    @Test
    void mapsSlugConflictToLegacyAdminError() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(forumTagAdministrationService.mutate(any()))
                .thenReturn(RpcResult.failure(AppErrorCode.FORUM_TAG_SLUG_CONFLICT, "trace-1"));
        CreateTagDTO dto = new CreateTagDTO();
        dto.setName("Java");

        assertThatThrownBy(() -> handler.create(dto, "java"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(AdminErrorCode.FORUM_TAG_SLUG_EXISTS.getCode());
    }

    @Test
    void mapsBadRequestToLegacyAdminError() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(forumTagAdministrationService.mutate(any()))
                .thenReturn(RpcResult.failure(AppErrorCode.BAD_REQUEST, "trace-1"));
        CreateTagDTO dto = new CreateTagDTO();
        dto.setName("Java");

        assertThatThrownBy(() -> handler.create(dto, "java"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(AdminErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    void mapsNullRpcResultToUnknownError() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(forumTagAdministrationService.mutate(any())).thenReturn(null);
        CreateTagDTO dto = new CreateTagDTO();
        dto.setName("Java");

        assertThatThrownBy(() -> handler.create(dto, "java"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(AdminErrorCode.UNKNOWN_ERROR.getCode());
    }

    @Test
    void rejectsMissingActorBeforeCallingOwner() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(" ");
        CreateTagDTO dto = new CreateTagDTO();
        dto.setName("Java");

        assertThatThrownBy(() -> handler.create(dto, "java"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(AdminErrorCode.UNAUTHORIZED.getCode());
        verify(forumTagAdministrationService, never()).mutate(any());
    }

    @Test
    void mapsForumTagReadRowsWithoutPrivateEntities() {
        when(forumTagReadPort.page("java", 1, 10, "usageCount", "desc"))
                .thenReturn(new ForumTagReadPort.ForumTagPage(List.of(
                        new ForumTagReadPort.ForumTagRow(
                                "tag-1", "Java", "java", null, "#fff", 3, null)), 1));

        var result = handler.list("java", 1, 10, "usageCount", "desc");

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getData()).singleElement().satisfies(tag -> {
            assertThat(tag.getId()).isEqualTo("tag-1");
            assertThat(tag.getType()).isEqualTo("FORUM");
            assertThat(tag.getUsageCount()).isEqualTo(3);
        });
    }
}
