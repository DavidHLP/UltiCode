package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.dto.ProblemExampleDTO;
import com.ulticode.app.api.service.ProblemExampleReadPort;
import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.JudgingCaseSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Legacy {@link JudgingCaseSource} backed by the {@code problem_examples}
 * table via the ProblemExampleReadPort. Legacy rows carry no hidden/sample flag,
 * so those fields are null and the pipeline leaves per-case scope unset
 * (matching the pre-refactor legacy path).
 */
@Component
@org.springframework.context.annotation.Profile("!test")
@RequiredArgsConstructor
public class ProblemExampleJudgingCaseSource implements JudgingCaseSource {

    private final ProblemExampleReadPort problemExampleReadPort;
    private final ObjectMapper objectMapper;

    @Override
    public List<JudgingCase> loadCases(long problemId) {
        List<ProblemExampleDTO> examples = problemExampleReadPort.findByProblemId(problemId);
        if (examples == null || examples.isEmpty()) {
            return List.of();
        }
        return examples.stream().map(dto -> new JudgingCase(
                dto.id(),
                dto.inputText(),
                dto.outputText(),
                JudgingCaseInputs.parse(objectMapper, dto.inputs(), dto.inputText(), dto.id()),
                null,
                null
        )).toList();
    }
}