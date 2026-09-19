package com.ulticode.submission.read;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.DefaultSubmissionProjection;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.read.SubmissionReadAssembly;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import com.ulticode.submission.api.dto.SubmissionVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionReadAssemblyTest {

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
