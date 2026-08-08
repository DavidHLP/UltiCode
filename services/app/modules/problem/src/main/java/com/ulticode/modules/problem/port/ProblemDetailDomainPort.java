package com.ulticode.modules.problem.port;

import com.ulticode.modules.problem.dto.UpdateProblemDTO;
import com.ulticode.modules.problem.entity.Problem;

public interface ProblemDetailDomainPort {
    void applyDetailUpdate(Long problemId, Problem problem, UpdateProblemDTO updateDTO);
}
