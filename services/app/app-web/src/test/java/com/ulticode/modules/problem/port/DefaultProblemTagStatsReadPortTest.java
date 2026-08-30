package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultProblemTagStatsReadPortTest {

    @Mock
    private ProblemTagRelationMapper relationMapper;
    @Mock
    private ProblemTagMapper tagMapper;
    @Mock
    private SubmissionUserStatsPort submissionUserStats;

    @Test
    void joinsOwnerAcceptedIdsWithAppOwnedTags() {
        ProblemTag arrays = tag("arrays", "Arrays");
        ProblemTag graphs = tag("graphs", "Graphs");
        ProblemTagRelation first = relation(1L, "arrays");
        ProblemTagRelation duplicate = relation(1L, "arrays");
        ProblemTagRelation second = relation(2L, "arrays");
        ProblemTagRelation third = relation(2L, "graphs");
        when(submissionUserStats.findAcceptedProblemIdsByUserId("user-1"))
                .thenReturn(List.of(1L, 2L));
        when(relationMapper.selectList(any())).thenReturn(List.of(first, duplicate, second, third));
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(arrays, graphs));

        List<Map<String, Object>> result = new DefaultProblemTagStatsReadPort(
                relationMapper, tagMapper, submissionUserStats).findTagStatsByUserId("user-1");

        assertThat(result).extracting(row -> row.get("tagSlug"))
                .containsExactly("arrays", "graphs");
        assertThat(result).extracting(row -> row.get("count"))
                .containsExactly(2L, 1L);
    }

    @Test
    void returnsEmptyWithoutAcceptedOwnerFacts() {
        when(submissionUserStats.findAcceptedProblemIdsByUserId("user-1"))
                .thenReturn(List.of());

        List<Map<String, Object>> result = new DefaultProblemTagStatsReadPort(
                relationMapper, tagMapper, submissionUserStats).findTagStatsByUserId("user-1");

        assertThat(result).isEmpty();
        verify(relationMapper, never()).selectList(any());
    }

    private static ProblemTag tag(String id, String label) {
        ProblemTag tag = new ProblemTag();
        tag.setId(id);
        tag.setLabel(label);
        tag.setSlug(id);
        return tag;
    }

    private static ProblemTagRelation relation(Long problemId, String tagId) {
        ProblemTagRelation relation = new ProblemTagRelation();
        relation.setProblemId(problemId);
        relation.setTagId(tagId);
        return relation;
    }
}
