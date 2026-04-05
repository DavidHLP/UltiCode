import { apiGet, apiPost } from "@/utils/request";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import type { ProblemRunResult } from "@/types/test-results";

// Helper to map backend snake_case to frontend camelCase
export function mapSubmission(sub: unknown): SubmissionRecord {
  if (!sub || typeof sub !== "object") return sub as SubmissionRecord;
  const s = sub as Record<string, unknown>;
  return {
    ...s,
    errorDetail: (s.error_detail ?? s.errorDetail) as string | undefined,
    runtimePercentile: (s.runtime_percentile ?? s.runtimePercentile) as
      | number
      | undefined,
    memoryPercentile: (s.memory_percentile ?? s.memoryPercentile) as
      | number
      | undefined,
  } as SubmissionRecord;
}

function mapSubmissionStatus(meta: unknown): SubmissionStatusMeta {
  if (!meta || typeof meta !== "object") return meta as SubmissionStatusMeta;
  const m = meta as Record<string, unknown>;
  return {
    key: m.key as SubmissionStatusMeta["key"],
    code: m.code as string,
    label: m.label as string,
    description: m.description as string | undefined,
    suggestion: m.suggestion as string | undefined,
    category: m.category as SubmissionStatusMeta["category"],
    severity: m.severity as SubmissionStatusMeta["severity"],
    isTerminal: Boolean(m.is_terminal ?? m.isTerminal),
    sortOrder: Number(m.sort_order ?? m.sortOrder ?? 0),
  } as SubmissionStatusMeta;
}

export async function fetchProblemSubmissions(
  problemId: number,
): Promise<SubmissionRecord[]> {
  const pageResult = await apiGet<{ items: unknown[] }>(`/problems/${problemId}/submissions`);
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
  return apiPost<ProblemRunResult>(`/problems/${problemId}/submissions/run`, {
    language: data.language,
    code: data.code,
    testCases: testCases.length > 0 ? testCases : undefined,
  });
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
