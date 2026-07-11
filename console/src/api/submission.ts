import { apiGet, apiPost } from "@/utils/request";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import type { ProblemRunResult } from "@/types/test-results";
import {
  mapDistributionBins,
  mapRunResult,
  mapSubmission,
  mapSubmissionStatus,
} from "@/api/projection";

// Re-export the decoders so existing call sites that import them from
// "@/api/submission" keep compiling.
export { mapDistributionBins, mapRunResult, mapSubmission };

// Re-export `mapSubmissionStatus` for tests / future callers; the public
// `fetchSubmissionStatuses` already routes through it below.
export { mapSubmissionStatus };

export async function fetchProblemSubmissions(
  problemId: number,
): Promise<SubmissionRecord[]> {
  const pageResult = await apiGet<{ items: unknown[] }>(
    `/problems/${problemId}/submissions`,
  );
  return pageResult.items.map(mapSubmission);
}

export async function fetchSubmission(
  submissionId: string,
): Promise<SubmissionRecord> {
  const data = await apiGet<unknown>(`/submissions/${submissionId}`);
  return mapSubmission(data);
}

export async function fetchBestSubmission(
  problemId: string,
): Promise<SubmissionRecord> {
  const data = await apiGet<unknown>(`/problems/${problemId}/submissions/best`);
  return mapSubmission(data);
}

export async function fetchUserSubmissions(): Promise<SubmissionRecord[]> {
  // Backend returns PageResult<SubmissionVO> with items array, not a plain array
  const pageResult = await apiGet<{ items: unknown[] }>(`/submissions`);
  return pageResult.items.map(mapSubmission);
}

export async function fetchSubmissionStatuses(): Promise<
  SubmissionStatusMeta[]
> {
  const data = await apiGet<unknown[]>(`/submissions/statuses`);
  return data.map(mapSubmissionStatus);
}

export async function createSubmission(
  problemId: number,
  data: { language: string; code: string },
): Promise<SubmissionRecord> {
  const response = await apiPost<unknown>(
    `/problems/${problemId}/submissions`,
    data,
  );
  return mapSubmission(response);
}

export async function runSubmission(
  problemId: number,
  data: {
    language: string;
    code: string;
    testCases?: {
      id: string;
      label?: string;
      output?: string;
      inputs?: { id?: string; label?: string; name: string; value: string }[];
    }[];
  },
): Promise<ProblemRunResult> {
  const testCases =
    data.testCases?.map((testCase) => {
      const inputs = Array.isArray(testCase.inputs) ? testCase.inputs : [];
      const normalizedInputs = inputs
        .filter(
          (input) =>
            input &&
            typeof input.name === "string" &&
            input.name.trim().length > 0,
        )
        .map((input) => ({
          id: input.id,
          label: input.label,
          name: input.name,
          value: typeof input.value === "string" ? input.value : "",
        }));
      return {
        id: testCase.id,
        label: testCase.label,
        output: testCase.output,
        inputs: normalizedInputs.length > 0 ? normalizedInputs : undefined,
      };
    }) ?? [];
  const response = await apiPost<unknown>(
    `/problems/${problemId}/submissions/run`,
    {
      language: data.language,
      code: data.code,
      testCases: testCases.length > 0 ? testCases : undefined,
    },
  );
  // Run endpoints use a distinct DTO (RunResultDTO) with different field
  // shapes (verdict, cases[], runtimeMs/memoryMb). Decoupled from
  // mapSubmission() to avoid silent type confusion.
  return mapRunResult(response);
}
export async function fetchDailyActivity(year?: number): Promise<string[]> {
  const params = new URLSearchParams();
  if (year) params.append("year", year.toString());
  const query = params.toString();
  return apiGet<string[]>(`/submissions/calendar${query ? `?${query}` : ""}`);
}

export interface MonthlySubmission {
  month: string;
  count: number;
  accepted: number;
}

export interface LanguageSubmission {
  language: string;
  count: number;
}

export interface SubmissionHistory {
  monthly: MonthlySubmission[];
  languages: LanguageSubmission[];
  totalSubmissions: number;
  totalAccepted: number;
  acceptanceRate: number;
}

export async function fetchSubmissionHistory(): Promise<SubmissionHistory> {
  return apiGet<SubmissionHistory>("/submissions/history");
}

export interface WeeklyProgress {
  week: string;
  solved: number;
  timeSpent: number; // in hours
}

export interface DifficultyProgress {
  difficulty: string;
  count: number;
  avgTime: number;
}

export interface LearningProgress {
  weeklyProgress: WeeklyProgress[];
  difficultyProgress: DifficultyProgress[];
  totalProblems: number;
  totalTimeHours: number;
  avgTimePerProblem: number;
  currentStreak: number;
  longestStreak: number;
}

export async function fetchLearningProgress(): Promise<LearningProgress> {
  return apiGet<LearningProgress>("/submissions/learning-progress");
}
