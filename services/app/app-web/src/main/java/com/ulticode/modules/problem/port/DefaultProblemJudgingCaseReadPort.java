package com.ulticode.modules.problem.port;

import com.ulticode.app.api.dto.ProblemJudgingCaseDTO;
import com.ulticode.app.api.service.ProblemJudgingCaseReadPort;
import com.ulticode.modules.problem.entity.TestCase;
import com.ulticode.modules.problem.mapper.TestCaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@org.springframework.context.annotation.Primary
@RequiredArgsConstructor
public class DefaultProblemJudgingCaseReadPort implements ProblemJudgingCaseReadPort {

    private final TestCaseMapper testCaseMapper;

    @Override
    public List<ProblemJudgingCaseDTO> loadCases(long problemId) {
        List<TestCase> cases = testCaseMapper.findActiveCasesForJudging(problemId);
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        return cases.stream()
                .map(tc -> new ProblemJudgingCaseDTO(
                        String.valueOf(tc.getId()),
                        tc.getTestOrder(),
                        tc.getInputText(),
                        tc.getOutputText(),
                        tc.getInputs(),
                        tc.getIsHidden(),
                        tc.getIsSample()
                ))
                .toList();
    }
}
