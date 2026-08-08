package com.ulticode.modules.problem.port;

public interface ProblemVersionPort {
    void createInitialVersion(Long problemId, String operatorId);
    void createVersion(Long problemId, String changeType, String changeSummary, String operatorId);
}
