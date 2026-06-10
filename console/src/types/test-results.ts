import type { SubmissionStatusKey } from "@/types/submission";

export interface ProblemRunCase {
  id: string;
  runId: string;
  submissionTestId: string;
  testCaseId: string;
  caseLabel: string;
  status: SubmissionStatusKey;
  runtime: string;
  memory: string;
  /** v2 numeric runtime in milliseconds (may be absent for legacy callers). */
  runtimeMs?: number;
  /** v2 numeric memory in MB (may be absent for legacy callers). */
  memoryMb?: number;
  detail?: string;
  output?: string;
  expectedOutput?: string;
  inputs?: { id: string; label: string; name: string; value: string }[];
}

export type ProblemCaseResultDetail = ProblemRunCase;

export interface ProblemRunResult {
  id: string;
  submissionId: string;
  problemId: number;
  userId: string;
  verdict: SubmissionStatusKey;
  runtime: string;
  memory: string;
  /** v2 numeric runtime in milliseconds. */
  runtimeMs?: number;
  /** v2 numeric memory in MB. */
  memoryMb?: number;
  cases: ProblemRunCase[];
  // Legacy or optional fields
  passed_cases?: number;
  total_cases?: number;
  errorMessage?: string;
  error_message?: string;
}
