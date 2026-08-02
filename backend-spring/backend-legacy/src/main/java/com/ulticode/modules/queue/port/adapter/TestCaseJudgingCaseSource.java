package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.dto.ProblemJudgingCaseDTO;
import com.ulticode.app.api.service.ProblemJudgingCaseReadPort;
import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.JudgingCaseSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Canonical {@link JudgingCaseSource} backed by the {@code test_cases}
 * table via the Problem app-api port. Reads active cases for a problem and maps
 * each to a {@link JudgingCase}, parsing inputs via the shared
 * {@link JudgingCaseInputs} policy. Uses the ProblemJudgingCaseReadPort to
 * abstract away the backend-app test case storage.
 */
@Component
@RequiredArgsConstructor
public class TestCaseJudgingCaseSource implements JudgingCaseSource {

    private final ProblemJudgingCaseReadPort port;
    private final ObjectMapper objectMapper;

    @Override
    public List<JudgingCase> loadCases(long problemId) {
        List<ProblemJudgingCaseDTO> cases = port.loadCases(problemId);
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        return cases.stream().map(dto -> new JudgingCase(
                dto.id(),
                "Case " + dto.testOrder(),
                dto.outputText(),
                JudgingCaseInputs.parse(objectMapper, dto.inputs(), dto.inputText(), dto.id()),
                null,
                null
        )).toList();
    }
}