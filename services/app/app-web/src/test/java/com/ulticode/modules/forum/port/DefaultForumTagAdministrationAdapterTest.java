package com.ulticode.modules.forum.port;

import com.ulticode.common.command.ActorDelegation;
import com.ulticode.app.api.command.ForumTagMutationCommand;
import com.ulticode.app.api.dto.ForumTagDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.forum.entity.ForumTag;
import com.ulticode.modules.forum.mapper.ForumTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultForumTagAdministrationAdapterTest {

    @Mock
    private ForumTagMapper forumTagMapper;

    private DefaultForumTagAdministrationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DefaultForumTagAdministrationAdapter(forumTagMapper);
    }

    @Test
    void updateTreatsAffectedRowsZeroAsSuccessWhenLockedRowStillExists() {
        ForumTag existing = tag("tag-1", "Java", "java");
        when(forumTagMapper.selectByIdForUpdate("tag-1")).thenReturn(existing, existing);
        when(forumTagMapper.updateById(existing)).thenReturn(0);

        RpcResult<ForumTagDTO> result = adapter.mutate(command(
                ForumTagMutationCommand.Action.UPDATE, "tag-1", null, null, "Java", null));

        assertThat(result.success()).isTrue();
        assertThat(result.data().id()).isEqualTo("tag-1");
        verify(forumTagMapper).updateById(existing);
    }

    @Test
    void updateDoesNotReturnSuccessWhenLockedRowDisappears() {
        ForumTag existing = tag("tag-1", "Java", "java");
        when(forumTagMapper.selectByIdForUpdate("tag-1")).thenReturn(existing, null);
        when(forumTagMapper.updateById(existing)).thenReturn(0);

        RpcResult<ForumTagDTO> result = adapter.mutate(command(
                ForumTagMutationCommand.Action.UPDATE, "tag-1", null, null, "Kotlin", null));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
    }

    @Test
    void deleteDoesNotReturnSuccessWhenAffectedRowsAreZero() {
        ForumTag existing = tag("tag-1", "Java", "java");
        when(forumTagMapper.selectByIdForUpdate("tag-1")).thenReturn(existing);
        when(forumTagMapper.deleteById("tag-1")).thenReturn(0);

        RpcResult<ForumTagDTO> result = adapter.mutate(command(
                ForumTagMutationCommand.Action.DELETE, "tag-1", null, null, null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.CONTENT_NOT_FOUND.code());
    }

    @Test
    void deleteReturnsDeletedRowSnapshotOnSuccess() {
        ForumTag existing = tag("tag-1", "Java", "java");
        when(forumTagMapper.selectByIdForUpdate("tag-1")).thenReturn(existing);
        when(forumTagMapper.deleteById("tag-1")).thenReturn(1);

        RpcResult<ForumTagDTO> result = adapter.mutate(command(
                ForumTagMutationCommand.Action.DELETE, "tag-1", null, null, null, null));

        assertThat(result.success()).isTrue();
        assertThat(result.data().id()).isEqualTo("tag-1");
    }

    @Test
    void mergeLocksBothTagsInDeterministicOrderBeforeDeletingSource() {
        ForumTag source = tag("z-source", "Java", "java");
        ForumTag target = tag("a-target", "JVM", "jvm");
        when(forumTagMapper.selectByIdForUpdate("a-target")).thenReturn(target);
        when(forumTagMapper.selectByIdForUpdate("z-source")).thenReturn(source);
        when(forumTagMapper.deleteById("z-source")).thenReturn(1);

        RpcResult<ForumTagDTO> result = adapter.mutate(command(
                ForumTagMutationCommand.Action.MERGE, null, "z-source", "a-target", null, null));

        assertThat(result.success()).isTrue();
        assertThat(result.data().id()).isEqualTo("a-target");
        InOrder order = inOrder(forumTagMapper);
        order.verify(forumTagMapper).selectByIdForUpdate("a-target");
        order.verify(forumTagMapper).selectByIdForUpdate("z-source");
        order.verify(forumTagMapper).deleteById("z-source");
    }

    private static ForumTagMutationCommand command(
            ForumTagMutationCommand.Action action,
            String tagId,
            String sourceTagId,
            String targetTagId,
            String name,
            String slug) {
        return new ForumTagMutationCommand(
                "command-1",
                IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "test"),
                TraceMetadata.EMPTY,
                action,
                tagId,
                sourceTagId,
                targetTagId,
                name,
                slug,
                null,
                null);
    }

    private static ForumTag tag(String id, String name, String slug) {
        ForumTag tag = new ForumTag();
        tag.setId(id);
        tag.setName(name);
        tag.setSlug(slug);
        tag.setUsageCount(0);
        return tag;
    }
}
