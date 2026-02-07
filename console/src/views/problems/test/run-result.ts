import type { ProblemTestCase } from "@/types/problem-detail";
import type { ProblemRunResult } from "@/types/test-results";
import { fetchCurrentUserId } from "@/utils/auth";

export const buildRunResultFromCases = (
  cases: ProblemTestCase[],
  problemId: number,
): ProblemRunResult => {
  const runId = `run-${problemId}-${Date.now()}`;
  const userId = fetchCurrentUserId();
  const runtimeMs = 40 + cases.length * 5;
  const memoryMb = 8 + cases.length * 2;

  const mappedCases = cases.map((testCase, index) => {
    const displayLabel = `Case ${index + 1}`;
    const output =
      testCase.inputs?.map((input) => input.value || "").join(" | ") || "OK";

    return {
      id: `${runId}-case-${index + 1}`,
      runId,
      submissionTestId: testCase.id,
      testCaseId: testCase.id,
      caseLabel: displayLabel,
      status: "Accepted" as const,
      runtime: `${Math.max(1, testCase.inputs?.length ?? 1) * 2} ms`,
      memory: `${10 + index} MB`,
      detail: "Sample run using provided inputs.",
      output,
      expectedOutput: output,
      inputs:
        testCase.inputs?.map((input) => ({
          id: input.id ?? `input-${index}-${input.name}`,
          label: input.label ?? input.name,
          name: input.name,
          value: input.value,
        })) ?? [],
    };
  });

  return {
    id: runId,
    submissionId: `${runId}-submission`,
    problemId,
    userId: userId || "anonymous",
    verdict: "Accepted",
    runtime: `${runtimeMs} ms`,
    memory: `${memoryMb} MB`,
    cases: mappedCases,
  };
};
