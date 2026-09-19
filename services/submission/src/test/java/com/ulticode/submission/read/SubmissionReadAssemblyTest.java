package com.ulticode.submission.read;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.DefaultSubmissionProjection;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.read.SubmissionReadAssembly;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import com.ulticode.submission.api.dto.PerformanceStats;
import com.ulticode.submission.api.dto.SubmissionDetailVO;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.api.dto.SubmissionVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmissionReadAssemblyTest {

    @Test
    void findByIdEnforcesOwnershipAndUsesDetailProjection() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        SubmissionProjection projection = mock(SubmissionProjection.class);
        SubmissionPerformanceStats performanceStats = mock(SubmissionPerformanceStats.class);
        ProblemFactsPort problemFactsPort = mock(ProblemFactsPort.class);
        Submission submission = submission("sub-1", 101L);
        SubmissionDetailVO detail = new SubmissionDetailVO();

        when(submissionMapper.selectById("sub-1")).thenReturn(submission);
        when(problemFactsPort.findDisplayFactsBatch(anyCollection())).thenReturn(Map.of());
        when(performanceStats.compute(any(Submission.class), anyInt(), any()))
                .thenReturn(PerformanceStats.EMPTY);
        when(projection.toDetailVO(any(Submission.class), any(PerformanceStats.class), anyMap()))
                .thenReturn(detail);

        SubmissionReadAssembly assembly = newAssembly(
                submissionMapper, projection, performanceStats, problemFactsPort);

        assertThat(assembly.findById("sub-1", "user-1")).isSameAs(detail);
        assertThat(assembly.findById("sub-1", "other-user")).isNull();
        assertThat(assembly.findById(null, "user-1")).isNull();
        verify(projection).toDetailVO(same(submission), same(PerformanceStats.EMPTY), anyMap());
    }

    @Test
    void listReadsNormalizePaginationAndSkipFactsForEmptyPages() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        SubmissionProjection projection = mock(SubmissionProjection.class);
        SubmissionPerformanceStats performanceStats = mock(SubmissionPerformanceStats.class);
        ProblemFactsPort problemFactsPort = mock(ProblemFactsPort.class);
        Page<Submission> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);

        when(submissionMapper.findByUserId(eq("user-1"), any(Page.class))).thenReturn(emptyPage);
        when(projection.toVO(anyList(), anyMap())).thenReturn(List.of());

        SubmissionReadAssembly assembly = newAssembly(
                submissionMapper, projection, performanceStats, problemFactsPort);
        SubmissionQueryDTO query = new SubmissionQueryDTO();
        query.setPage(0);
        query.setPageSize(0);

        var result = assembly.findByUserId("user-1", query);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        verify(problemFactsPort, never()).findDisplayFactsBatch(anyCollection());
    }

    @Test
    void problemListAndBestReadsUsePartialFactsWithoutCrossOwnerAccess() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        SubmissionProjection projection = mock(SubmissionProjection.class);
        SubmissionPerformanceStats performanceStats = mock(SubmissionPerformanceStats.class);
        ProblemFactsPort problemFactsPort = mock(ProblemFactsPort.class);
        Submission submission = submission("sub-2", 202L);
        SubmissionListItemVO listItem = new SubmissionListItemVO();
        SubmissionVO best = new SubmissionVO();
        Page<Submission> page = new Page<>(1, 10);
        page.setRecords(List.of(submission));
        page.setTotal(1);

        when(submissionMapper.findByProblemId(eq(202L), eq("user-1"), any(Page.class)))
                .thenReturn(page);
        when(submissionMapper.findBestByProblemIdAndUserId(202L, "user-1"))
                .thenReturn(Optional.of(submission));
        when(problemFactsPort.findDisplayFactsBatch(anyCollection())).thenReturn(Map.of());
        when(projection.toListItemVO(same(submission), isNull())).thenReturn(listItem);
        when(projection.toVO(same(submission), anyMap())).thenReturn(best);

        SubmissionReadAssembly assembly = newAssembly(
                submissionMapper, projection, performanceStats, problemFactsPort);

        var list = assembly.findByProblemId(202L, "user-1", new SubmissionQueryDTO());
        assertThat(list.getItems()).containsExactly(listItem);
        assertThat(list.getTotal()).isEqualTo(1L);
        assertThat(assembly.findBest(202L, "user-1")).isSameAs(best);
        assertThat(assembly.findBest(null, "user-1")).isNull();
        assertThat(assembly.findBest(202L, null)).isNull();
        verify(projection).toListItemVO(same(submission), isNull());
        verify(projection).toVO(same(submission), anyMap());
    }

    @Test
    void toVOsKeepsRequestedOrderAcrossChunksAndToleratesPartialFacts() {
        SubmissionMapper submissionMapper = mock(SubmissionMapper.class);
        ProblemFactsPort problemFactsPort = mock(ProblemFactsPort.class);
        SubmissionUserReadPort userReadPort = mock(SubmissionUserReadPort.class);
        when(userReadPort.findAllById(anyCollection())).thenReturn(Map.of());

        List<List<String>> storageRequests = new ArrayList<>();
        when(submissionMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            Collection<String> ids = invocation.getArgument(0);
            List<String> requested = List.copyOf(ids);
            storageRequests.add(requested);
            List<Submission> rows = requested.stream()
                    .map(id -> submission(id, Integer.parseInt(id.substring("sub-".length())) + 1L))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.reverse(rows);
            return rows;
        });

        List<Collection<Long>> factRequests = new ArrayList<>();
        when(problemFactsPort.findDisplayFactsBatch(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            factRequests.add(List.copyOf(ids));
            return ids.stream()
                    .filter(id -> id % 2 == 0)
                    .collect(Collectors.toMap(
                            id -> id,
                            id -> new ProblemFactsPort.ProblemDisplayFacts(id, "Problem " + id, "p-" + id),
                            (left, right) -> left,
                            LinkedHashMap::new));
        });

        SubmissionProjection projection = new DefaultSubmissionProjection(
                submissionMapper, userReadPort, problemFactsPort, new ObjectMapper());
        SubmissionReadAssembly assembly = new SubmissionReadAssembly(
                submissionMapper, projection, mock(SubmissionPerformanceStats.class), problemFactsPort);

        List<String> requested = IntStream.range(0, 101)
                .mapToObj(i -> "sub-" + i)
                .toList();

        List<SubmissionVO> result = assembly.toVOs(requested);

        assertThat(result).extracting(SubmissionVO::getId)
                .containsExactlyElementsOf(requested);
        assertThat(result.get(0).getProblem()).isNull();
        assertThat(result.get(1).getProblem()).isNotNull();
        assertThat(result.get(1).getProblem().getId()).isEqualTo(2L);
        assertThat(storageRequests).extracting(Collection::size).containsExactly(100, 1);
        assertThat(storageRequests.get(0)).containsExactlyElementsOf(requested.subList(0, 100));
        assertThat(storageRequests.get(1)).containsExactly("sub-100");
        assertThat(factRequests).extracting(Collection::size).containsExactly(100, 1);
        assertThat(factRequests.get(0)).containsExactlyElementsOf(
                IntStream.range(1, 101).mapToObj(Long::valueOf).toList());
        assertThat(factRequests.get(1)).containsExactly(101L);
    }

    private static SubmissionReadAssembly newAssembly(
            SubmissionMapper submissionMapper,
            SubmissionProjection projection,
            SubmissionPerformanceStats performanceStats,
            ProblemFactsPort problemFactsPort) {
        return new SubmissionReadAssembly(
                submissionMapper, projection, performanceStats, problemFactsPort);
    }

    private static Submission submission(String id, Long problemId) {
        Submission submission = new Submission();
        submission.setId(id);
        submission.setProblemId(problemId);
        submission.setUserId("user-1");
        submission.setLanguage("java");
        submission.setCode("return 1;");
        submission.setStatus("Accepted");
        return submission;
    }
}
