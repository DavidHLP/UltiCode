package com.ulticode.app.dubbo.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.ForumCommentModerationCommand;
import com.ulticode.app.api.command.ForumPostModerationCommand;
import com.ulticode.app.api.command.ForumTagMutationCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.dto.AdminForumPostPage;
import com.ulticode.app.api.dto.AdminForumPostQuery;
import com.ulticode.app.api.dto.ForumCommentModerationResultDTO;
import com.ulticode.app.api.dto.ForumPostModerationResultDTO;
import com.ulticode.app.api.dto.ForumTagDTO;
import com.ulticode.app.idempotency.CommandReceiptExecutor;
import com.ulticode.app.security.AdminActorAuthorizer;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.forum.port.DefaultAdminForumReadAdapter;
import com.ulticode.modules.forum.port.DefaultForumCommentAdministrationAdapter;
import com.ulticode.modules.forum.port.DefaultForumOwnerPort;
import com.ulticode.modules.forum.port.DefaultForumTagAdministrationAdapter;
import com.ulticode.modules.vote.port.adapter.ForumPostVoteCountReadAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumAdministrationProviderTest {

    @Mock
    private DefaultAdminForumReadAdapter adminForumReadAdapter;

    @Mock
    private ForumPostVoteCountReadAdapter voteCountReadAdapter;

    @Mock
    private DefaultForumCommentAdministrationAdapter commentAdministrationAdapter;

    @Mock
    private DefaultForumTagAdministrationAdapter tagAdministrationAdapter;

    @Mock
    private DefaultForumOwnerPort forumOwnerPort;
    @Mock
    private AppCommandReceiptMapper receiptMapper;

    @Mock
    private AdminActorAuthorizer actorAuthorizer;

    private CommandReceiptExecutor receiptExecutor;

    @BeforeEach
    void setUp() {
        lenient().when(receiptMapper.insertClaim(any())).thenReturn(1);
        lenient().when(receiptMapper.markSuccess(any(), any())).thenReturn(1);
        lenient().when(actorAuthorizer.isAuthorized(any())).thenReturn(true);
        receiptExecutor = new CommandReceiptExecutor(
                receiptMapper, new ObjectMapper(), java.time.Clock.systemUTC());
    }

    @Test
    void forumReadProviderDelegatesEntityFreeReadContract() {
        ForumAdminReadProvider provider = new ForumAdminReadProvider(adminForumReadAdapter);
        AdminForumPostQuery query = new AdminForumPostQuery(
                null, null, null, null, null, null, null, "createdAt", "desc", 1, 10);
        AdminForumPostPage expected = new AdminForumPostPage(List.of(), 0);
        when(adminForumReadAdapter.listPosts(query)).thenReturn(expected);

        assertThat(provider.listPosts(query)).isSameAs(expected);
        verify(adminForumReadAdapter).listPosts(query);
    }

    @Test
    void voteCountProviderDelegatesBoundedCountContract() {
        ForumPostVoteCountReadProvider provider = new ForumPostVoteCountReadProvider(voteCountReadAdapter);
        List<String> postIds = List.of("post-1");
        when(voteCountReadAdapter.countVoteUpByTargets(postIds)).thenReturn(Map.of("post-1", 2L));

        assertThat(provider.countVoteUpByTargets(postIds)).containsEntry("post-1", 2L);
        verify(voteCountReadAdapter).countVoteUpByTargets(postIds);
    }

    @Test
    void commentProviderDelegatesMutationCommand() {
        ForumCommentAdministrationProvider provider =
                new ForumCommentAdministrationProvider(commentAdministrationAdapter, receiptExecutor, actorAuthorizer);
        ForumCommentModerationCommand command = new ForumCommentModerationCommand(
                "command-1", IdMetadata.mint(), actor("comment"), TraceMetadata.EMPTY,
                "comment-1", ForumCommentModerationCommand.Action.FLAG, "spam", null);
        ForumCommentModerationResultDTO dto = new ForumCommentModerationResultDTO(
                "comment-1", ForumCommentModerationCommand.Action.FLAG, "author-1",
                false, "", false);
        when(commentAdministrationAdapter.moderate(command)).thenReturn(RpcResult.success(dto, "trace-1"));

        assertThat(provider.moderate(command).data()).isEqualTo(dto);
        verify(commentAdministrationAdapter).moderate(command);
    }

    @Test
    void tagProviderDelegatesMutationCommand() {
        ForumTagAdministrationProvider provider =
                new ForumTagAdministrationProvider(tagAdministrationAdapter, receiptExecutor, actorAuthorizer);
        ForumTagMutationCommand command = new ForumTagMutationCommand(
                "command-1", IdMetadata.mint(), actor("tag"), TraceMetadata.EMPTY,
                ForumTagMutationCommand.Action.CREATE, null, null, null,
                "Java", "java", null, null);
        ForumTagDTO dto = new ForumTagDTO("tag-1", "Java", "java", null, null, 0, null);
        when(tagAdministrationAdapter.mutate(command)).thenReturn(RpcResult.success(dto, "trace-1"));

        assertThat(provider.mutate(command).data()).isEqualTo(dto);
        verify(tagAdministrationAdapter).mutate(command);
    }

    @Test
    void ownerProviderDelegatesMutationCommand() {
        ForumOwnerProvider provider = new ForumOwnerProvider(forumOwnerPort, receiptExecutor, actorAuthorizer);
        ForumPostModerationCommand command = new ForumPostModerationCommand(
                "command-owner", IdMetadata.mint(), actor("owner"), TraceMetadata.EMPTY,
                "post-1", ForumPostModerationCommand.Action.PIN, null);
        ForumPostModerationResultDTO expected = new ForumPostModerationResultDTO(
                "post-1", ForumPostModerationCommand.Action.PIN, "author-1", false, null);
        when(forumOwnerPort.moderate(command)).thenReturn(RpcResult.success(expected, "trace-1"));

        assertThat(provider.moderate(command).data()).isEqualTo(expected);
        verify(forumOwnerPort).moderate(command);
    }


    @Test
    void ownerProviderRejectsUnverifiedActorBeforeReceiptClaim() {
        ForumOwnerProvider provider = new ForumOwnerProvider(forumOwnerPort, receiptExecutor, actorAuthorizer);
        ForumPostModerationCommand command = new ForumPostModerationCommand(
                "command-owner", IdMetadata.mint(), actor("owner"), TraceMetadata.EMPTY,
                "post-1", ForumPostModerationCommand.Action.PIN, null);
        when(actorAuthorizer.isAuthorized(command.actor())).thenReturn(false);

        RpcResult<ForumPostModerationResultDTO> result = provider.moderate(command);

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verify(forumOwnerPort, never()).moderate(command);
        verify(receiptMapper, never()).insertClaim(any());
    }

    @Test
    void mutationProvidersRejectActorDelegatorMismatchBeforeReceiptClaim() {
        ActorDelegation mismatched = new ActorDelegation(
                "ADMIN", "admin-1", "different-admin", "mismatched");

        ForumCommentModerationCommand commentCommand = new ForumCommentModerationCommand(
                "command-comment", IdMetadata.mint(), mismatched, TraceMetadata.EMPTY,
                "comment-1", ForumCommentModerationCommand.Action.FLAG, "spam", null);
        RpcResult<?> commentResult = new ForumCommentAdministrationProvider(
                commentAdministrationAdapter, receiptExecutor, actorAuthorizer).moderate(commentCommand);

        ForumTagMutationCommand tagCommand = new ForumTagMutationCommand(
                "command-tag", IdMetadata.mint(), mismatched, TraceMetadata.EMPTY,
                ForumTagMutationCommand.Action.CREATE, null, null, null,
                "Java", "java", null, null);
        RpcResult<?> tagResult = new ForumTagAdministrationProvider(
                tagAdministrationAdapter, receiptExecutor, actorAuthorizer).mutate(tagCommand);

        ForumPostModerationCommand postCommand = new ForumPostModerationCommand(
                "command-post", IdMetadata.mint(), mismatched, TraceMetadata.EMPTY,
                "post-1", ForumPostModerationCommand.Action.PIN, null);
        RpcResult<?> postResult = new ForumOwnerProvider(
                forumOwnerPort, receiptExecutor, actorAuthorizer).moderate(postCommand);

        assertThat(commentResult.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        assertThat(tagResult.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        assertThat(postResult.error().code()).isEqualTo(AppErrorCode.FORBIDDEN.code());
        verify(receiptMapper, never()).insertClaim(any());
    }
    private static ActorDelegation actor(String rationale) {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", rationale);
    }
}
