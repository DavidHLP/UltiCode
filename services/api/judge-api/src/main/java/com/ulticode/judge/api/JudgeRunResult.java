package com.ulticode.judge.api;

import java.io.Serializable;
import java.util.List;

/** Immutable public preview result returned by Judge. */
public record JudgeRunResult(
        String id,
        Long problemId,
        String userId,
        String verdict,
        String runtime,
        String memory,
        Long runtimeMs,
        Double memoryMb,
        Long runtimeUs,
        Long cpuMs,
        List<CaseResult> cases,
        int passedCases,
        int totalCases,
        String errorMessage) implements Serializable {

    private static final long serialVersionUID = 1L;

    public JudgeRunResult {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public record CaseResult(
            String id,
            String runId,
            String submissionTestId,
            String testCaseId,
            String caseLabel,
            String status,
            String runtime,
            String memory,
            Long runtimeMs,
            Double memoryMb,
            Long runtimeUs,
            Long cpuMs,
            String detail,
            String output,
            String expectedOutput,
            List<InputParam> inputs) implements Serializable {
        private static final long serialVersionUID = 1L;

        public CaseResult {
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
        }
    }

    public record InputParam(
            String id,
            String label,
            String name,
            String value) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
