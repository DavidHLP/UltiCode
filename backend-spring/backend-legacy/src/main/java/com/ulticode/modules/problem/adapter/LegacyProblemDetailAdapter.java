package com.ulticode.modules.problem.adapter;

import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.port.ProblemDetailDomainPort;
import com.ulticode.modules.problem.port.ProblemDetailPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyProblemDetailAdapter implements ProblemDetailDomainPort {

    private final ProblemDetailPort problemDetailPort;

    @Override
    public void applyDetailUpdate(Long problemId, Problem problem, UpdateProblemDTO updateDTO) {
        problemDetailPort.applyDetailUpdate(problemId, problem, updateDTO);
    }
}
