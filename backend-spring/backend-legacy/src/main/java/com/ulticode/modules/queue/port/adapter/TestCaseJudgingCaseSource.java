package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.JudgingCaseSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Canonical {@link JudgingCaseSource} backed by the {@code test_cases}
 * table. Reads active cases for a problem and maps each to a {@link JudgingCase},
 * parsing inputs via the shared {@link JudgingCaseInputs} policy. Preserves the
 * hidden/sample flags so the pipeline can resolve per-case scope.
 */
@Component
@RequiredArgsConstructor
public class TestCaseJudgingCaseSource implements JudgingCaseSource {

    private final TestCaseMapper testCaseMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<JudgingCase> loadCases(long problemId) {
        List<TestCase> cases = testCaseMapper.findActiveCasesForJudging(problemId);
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        return cases.stream().map(tc -> new JudgingCase(
                tc.getId(),
                "Case " + tc.getTestOrder(),
                tc.getOutputText(),
                JudgingCaseInputs.parse(objectMapper, tc.getInputs(), tc.getInputText(), tc.getId()),
                tc.getIsHidden(),
                tc.getIsSample()
        )).toList();
    }
}
