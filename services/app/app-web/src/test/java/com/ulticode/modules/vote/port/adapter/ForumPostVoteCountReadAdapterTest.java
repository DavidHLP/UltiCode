package com.ulticode.modules.vote.port.adapter;

import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import com.ulticode.modules.vote.mapper.EdgeOperationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumPostVoteCountReadAdapterTest {

    @Mock
    private EdgeOperationMapper edgeOperationMapper;

    private ForumPostVoteCountReadAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ForumPostVoteCountReadAdapter(edgeOperationMapper);
    }

    @Test
    void countsOnlyForumPostVotesThroughOneBoundedBatchQuery() {
        List<String> postIds = List.of("post-1", "post-2");
        when(edgeOperationMapper.countByTargetsAndOperation(
                postIds, EdgeOperationTargetType.FORUM_POST.getValue(), EdgeOperationType.VOTE_UP.getValue()))
                .thenReturn(List.of(Map.of("target_id", "post-1", "cnt", 3L)));

        var result = adapter.countVoteUpByTargets(postIds);

        assertThat(result).containsEntry("post-1", 3L);
        verify(edgeOperationMapper).countByTargetsAndOperation(
                eq(postIds), eq(EdgeOperationTargetType.FORUM_POST.getValue()),
                eq(EdgeOperationType.VOTE_UP.getValue()));
    }

    @Test
    void emptyOrNullInputDoesNotTouchVoteMapper() {
        assertThat(adapter.countVoteUpByTargets(List.of())).isEmpty();
        assertThat(adapter.countVoteDownByTargets(null)).isEmpty();

        verify(edgeOperationMapper, never()).countByTargetsAndOperation(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
