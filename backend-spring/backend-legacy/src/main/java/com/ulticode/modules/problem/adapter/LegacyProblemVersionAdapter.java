package com.ulticode.modules.problem.adapter;

import com.ulticode.modules.problem.port.ProblemVersionPort;
import com.ulticode.modules.problem.service.ProblemVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyProblemVersionAdapter implements ProblemVersionPort {

    private final ProblemVersionService problemVersionService;

    @Override
    public void createInitialVersion(Long problemId, String operatorId) {
        problemVersionService.createInitialVersion(problemId, operatorId);
    }

    @Override
    public void createVersion(Long problemId, String changeType, String changeSummary, String operatorId) {
        problemVersionService.createVersion(problemId, changeType, changeSummary, operatorId);
    }
}
