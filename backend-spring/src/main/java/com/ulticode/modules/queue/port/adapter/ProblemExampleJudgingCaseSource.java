package com.ulticode.modules.queue.port.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.problem.entity.ProblemExample;
import com.ulticode.modules.problem.mapper.ProblemExampleMapper;
import com.ulticode.modules.queue.port.JudgingCase;
import com.ulticode.modules.queue.port.JudgingCaseSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Legacy {@link JudgingCaseSource} backed by the {@code problem_examples}
 * table. Legacy rows carry no hidden/sample flag, so those fields are null
 * and the pipeline leaves per-case scope unset (matching the pre-refactor
 * legacy path).
 */
@Component
@RequiredArgsConstructor
public class ProblemExampleJudgingCaseSource implements JudgingCaseSource {

    private final ProblemExampleMapper problemExampleMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<JudgingCase> loadCases(long problemId) {
        List<ProblemExample> examples = problemExampleMapper.findByProblemIdOrderByOrder(problemId);
        if (examples == null || examples.isEmpty()) {
            return List.of();
        }
        return examples.stream().map(tc -> new JudgingCase(
                String.valueOf(tc.getId()),
                "Case " + tc.getExampleOrder(),
                tc.getOutputText(),
                JudgingCaseInputs.parse(objectMapper, tc.getInputs(), tc.getInputText(), tc.getId()),
                null,
                null
        )).toList();
    }
}
