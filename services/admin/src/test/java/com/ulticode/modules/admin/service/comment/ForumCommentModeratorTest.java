package com.ulticode.modules.admin.service.comment;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.app.api.command.ForumCommentModerationCommand;
import com.ulticode.app.api.dto.ForumCommentModerationResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ForumCommentAdministrationService;
import com.ulticode.app.api.service.ForumCommentReadPort;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.util.AuditContext;
import com.ulticode.modules.admin.dto.AdminCommentQueryDTO;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumCommentModeratorTest {

    @Mock
    private ForumCommentReadPort forumCommentReadPort;

    @Mock
    private ForumCommentAdministrationService forumCommentAdministrationService;

    @Mock
    private AdminCommentReadPort commentReadPort;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private ForumCommentModerator moderator;

    @BeforeEach
    void setUp() {
        moderator = new ForumCommentModerator(
                forumCommentReadPort,
                forumCommentAdministrationService,
                commentReadPort,
                currentUserProvider);
        // Actor stubbing belongs only to mutation cases; read-only list tests do not resolve a user.
    }

    @AfterEach
    void clearAuditContext() {
        AuditContext.clear();
    }

    @Test
    void flagRoutesTypedCommandWithActorAndPreservesAuditDiff() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(forumCommentAdministrationService.moderate(any()))
                .thenReturn(RpcResult.success(new ForumCommentModerationResultDTO(
                        "comment-1", ForumCommentModerationCommand.Action.FLAG, "author-1",
                        false, "", false), "trace-1"));

        moderator.flagComment("comment-1", "spam");

        ArgumentCaptor<ForumCommentModerationCommand> captor =
                ArgumentCaptor.forClass(ForumCommentModerationCommand.class);
        verify(forumCommentAdministrationService).moderate(captor.capture());
        ForumCommentModerationCommand command = captor.getValue();
        assertThat(command.commentId()).isEqualTo("comment-1");
        assertThat(command.action()).isEqualTo(ForumCommentModerationCommand.Action.FLAG);
        assertThat(command.reason()).isEqualTo("spam");
        assertThat(command.actor().actorId()).isEqualTo("admin-1");
        assertThat(command.deletedBy()).isNull();
        assertThat(AuditContext.getUserId()).isEqualTo("author-1");
        assertThat(AuditContext.getOldValues()).containsEntry("isFlagged", false);
        assertThat(AuditContext.getNewValues()).containsEntry("isFlagged", true);
    }

    @Test
    void deleteRoutesAuthenticatedActorAsDeletedBy() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(forumCommentAdministrationService.moderate(any()))
                .thenReturn(RpcResult.success(new ForumCommentModerationResultDTO(
                        "comment-1", ForumCommentModerationCommand.Action.DELETE, "author-1",
                        false, null, false), "trace-1"));

        moderator.deleteComment("comment-1");

        ArgumentCaptor<ForumCommentModerationCommand> captor =
                ArgumentCaptor.forClass(ForumCommentModerationCommand.class);
        verify(forumCommentAdministrationService).moderate(captor.capture());
        ForumCommentModerationCommand command = captor.getValue();
        assertThat(command.action()).isEqualTo(ForumCommentModerationCommand.Action.DELETE);
        assertThat(command.actor().actorId()).isEqualTo("admin-1");
        assertThat(command.deletedBy()).isEqualTo("admin-1");
        assertThat(AuditContext.getOldValues()).containsEntry("isDeleted", false);
        assertThat(AuditContext.getNewValues()).containsEntry("isDeleted", true);
    }

    @Test
    void mapsOwnerNotFoundToAdminNotFound() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(forumCommentAdministrationService.moderate(any()))
                .thenReturn(RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, "trace-1"));

        assertThatThrownBy(() -> moderator.unflagComment("missing"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(AdminErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void mapsBadRequestToLegacyAdminError() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(forumCommentAdministrationService.moderate(any()))
                .thenReturn(RpcResult.failure(AppErrorCode.BAD_REQUEST, "trace-1"));

        assertThatThrownBy(() -> moderator.unflagComment("comment-1"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(AdminErrorCode.BAD_REQUEST.getCode());
    }


    @Test
    void throwsUnknownErrorOnNullRpcResult() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(forumCommentAdministrationService.moderate(any())).thenReturn(null);

        assertThatThrownBy(() -> moderator.flagComment("comment-1", "spam"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(AdminErrorCode.UNKNOWN_ERROR.getCode());
    }

    @Test
    void throwsUnknownErrorOnNullResultData() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("admin-1");
        when(forumCommentAdministrationService.moderate(any()))
                .thenReturn(new RpcResult<>(true, null, null, null, "trace-1", null, null));

        assertThatThrownBy(() -> moderator.unflagComment("comment-1"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .extracting("code")
                .isEqualTo(AdminErrorCode.UNKNOWN_ERROR.getCode());
    }
    @Test
    void listUsesOwnerReadAndBoundedEnrichment() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        ForumCommentReadPort.ForumCommentRow row = new ForumCommentReadPort.ForumCommentRow(
                "comment-1", "body", createdAt, null, "author-1", null, "post-1",
                false, null, null, false, null, null);
        when(forumCommentReadPort.page(false, false, "body", "post-1", "createdAt", "desc", 1, 10))
                .thenReturn(new ForumCommentReadPort.ForumCommentPage(List.of(row), 1));
        when(commentReadPort.findAuthorSummariesByIds(Set.of("author-1")))
                .thenReturn(Map.of("author-1", new AdminCommentReadPort.AuthorSummary(
                        "author-1", "alice", "/avatar.png")));
        when(commentReadPort.findForumPostTitlesByIds(Set.of("post-1")))
                .thenReturn(Map.of("post-1", "Post title"));
        AdminCommentQueryDTO query = new AdminCommentQueryDTO();
        query.setIsFlagged(false);
        query.setIsDeleted(false);
        query.setSearch("body");
        query.setParentEntityId("post-1");
        query.setSortBy("createdAt");
        query.setSortOrder("desc");

        var result = moderator.listComments(query, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(comment -> {
            assertThat(comment.parentTitle()).isEqualTo("Post title");
            assertThat(comment.author()).extracting("username").isEqualTo("alice");
        });
        verify(commentReadPort).findAuthorSummariesByIds(Set.of("author-1"));
        verify(commentReadPort).findForumPostTitlesByIds(Set.of("post-1"));
    }
}
