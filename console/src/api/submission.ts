import { apiGet, apiPost } from "@/utils/request";
import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import type { ProblemRunResult, ProblemRunCase } from "@/types/test-results";

// ============================================================================
// Backend Response Interfaces (snake_case from Spring Boot)
// ============================================================================

interface BackendSubmissionRecord {
  error_detail?: unknown;
  errorDetail?: unknown;
  runtime_percentile?: unknown;
  runtimePercentile?: unknown;
  memory_percentile?: unknown;
  memoryPercentile?: unknown;
  runtime_dist_bins_ms?: unknown;
  runtimeDistBinsMs?: unknown;
  memory_dist_bins_mb?: unknown;
  memoryDistBinsMb?: unknown;
  [key: string]: unknown;
}

interface BackendSubmissionStatusMeta {
  key?: unknown;
  code?: unknown;
  label?: unknown;
  description?: unknown;
  suggestion?: unknown;
  category?: unknown;
  severity?: unknown;
  is_terminal?: unknown;
  isTerminal?: unknown;
  sort_order?: unknown;
  sortOrder?: unknown;
}

interface BackendRunResult {
  id?: unknown;
  problemId?: unknown;
  userId?: unknown;
  verdict?: unknown;
  runtime?: unknown;
  runtimeMs?: unknown;
  memory?: unknown;
  memoryMb?: unknown;
  cases?: unknown;
  passedCases?: unknown;
  passed_cases?: unknown;
  totalCases?: unknown;
  total_cases?: unknown;
  errorMessage?: unknown;
  error_message?: unknown;
  [key: string]: unknown;
}

interface BackendRunCase {
  id?: unknown;
  runId?: unknown;
  submissionTestId?: unknown;
  testCaseId?: unknown;
  caseLabel?: unknown;
  status?: unknown;
  runtime?: unknown;
  runtimeMs?: unknown;
  memory?: unknown;
  memoryMb?: unknown;
  detail?: unknown;
  output?: unknown;
  expectedOutput?: unknown;
  inputs?: unknown;
  [key: string]: unknown;
}

// ============================================================================
// Helpers
// ============================================================================

/**
 * Normalize backend `memoryDistBinsMb` / `runtimeDistBinsMs` field.
 *
 * <p>Since the v2 schema fix (2026-06-10), backend consistently returns
 * `number[]` for these fields. This helper remains for backward compatibility
 * with transitional windows where a JSON string may still be served, and as
 * a defensive measure against future schema drift.
 *
 * <p>Always returns `number[]`; empty array on parse failure.
 *
 * @see docs/reports/submission-api-test-report-2026-06-10.md §4.2
 */
export function mapDistributionBins(raw: unknown): number[] {
  if (Array.isArray(raw)) {
    return raw.filter((v): v is number => typeof v === "number");
  }
  if (typeof raw === "string") {
    try {
      const parsed: unknown = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        return parsed.filter((v): v is number => typeof v === "number");
      }
    } catch {
      // fall through to empty
    }
  }
  return [];
}

// Helper to map backend snake_case to frontend camelCase
export function mapSubmission(sub: unknown): SubmissionRecord {
  if (!sub || typeof sub !== "object") return sub as SubmissionRecord;
  const s = sub as BackendSubmissionRecord;
  return {
    ...s,
    created_at: (s.created_at ?? s.createdAt) as string,
    submittedAt: (s.submitted_at ??
      s.submittedAt ??
      s.created_at ??
      s.createdAt) as string | undefined,
    errorDetail: (s.error_detail ?? s.errorDetail) as string | undefined,
    runtimePercentile: (s.runtime_percentile ?? s.runtimePercentile) as
      | number
      | undefined,
    memoryPercentile: (s.memory_percentile ?? s.memoryPercentile) as
      | number
      | undefined,
    // v2 schema: backend returns number[]; helper still tolerates legacy JSON string.
    runtimeDistBinsMs: mapDistributionBins(
      s.runtime_dist_bins_ms ?? s.runtimeDistBinsMs,
    ),
    memoryDistBinsMb: mapDistributionBins(
      s.memory_dist_bins_mb ?? s.memoryDistBinsMb,
    ),
  } as SubmissionRecord;
}

function mapSubmissionStatus(meta: unknown): SubmissionStatusMeta {
  if (!meta || typeof meta !== "object") return meta as SubmissionStatusMeta;
  const m = meta as BackendSubmissionStatusMeta;
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

/**
 * Map backend `RunResultDTO` to frontend `ProblemRunResult`.
 *
 * <p>Distinct from `mapSubmission()` because Run endpoints have a different
 * field shape:
 * <ul>
 *   <li>`problemId`: numeric `Long` since v2 (was `String` in legacy DTO)</li>
 *   <li>`verdict`: top-level status (not `status`)</li>
 *   <li>`cases[]`: per-case results (not `tests[]`)</li>
 *   <li>`runtimeMs` / `memoryMb`: numeric v2 fields (alongside formatted strings)</li>
 * </ul>
 *
 * @see docs/reports/submission-api-test-report-2026-06-10.md §4.1
 */
export function mapRunResult(raw: unknown): ProblemRunResult {
  if (!raw || typeof raw !== "object") return raw as ProblemRunResult;
  const r = raw as BackendRunResult;

  const cases = Array.isArray(r.cases)
    ? r.cases.map(mapRunCase)
    : ([] as ProblemRunCase[]);

  return {
    id: String(r.id ?? ""),
    submissionId: String(r.id ?? ""),
    problemId: Number(r.problemId ?? 0),
    userId: String(r.userId ?? ""),
    verdict: String(r.verdict ?? "Runtime Error") as ProblemRunResult["verdict"],
    runtime: String(r.runtime ?? ""),
    memory: String(r.memory ?? ""),
    runtimeMs: typeof r.runtimeMs === "number" ? r.runtimeMs : undefined,
    memoryMb: typeof r.memoryMb === "number" ? r.memoryMb : undefined,
    cases,
    passed_cases: Number(r.passedCases ?? r.passed_cases ?? 0),
    total_cases: Number(r.totalCases ?? r.total_cases ?? 0),
    errorMessage: (r.errorMessage ?? r.error_message) as string | undefined,
  };
}

function mapRunCase(raw: unknown): ProblemRunCase {
  if (!raw || typeof raw !== "object") {
    // Defensive fallback: backend should never send non-object cases, but
    // keep a valid empty case rather than propagating null to the UI.
    return {
      id: "",
      runId: "",
      submissionTestId: "",
      testCaseId: "",
      caseLabel: "",
      status: "Runtime Error",
      runtime: "0ms",
      memory: "0.0MB",
    };
  }
  const c = raw as BackendRunCase;
  return {
    id: String(c.id ?? ""),
    runId: String(c.runId ?? ""),
    submissionTestId: (c.submissionTestId as string) ?? "",
    testCaseId: (c.testCaseId as string) ?? "",
    caseLabel: (c.caseLabel as string) ?? "",
    status: String(c.status ?? "Runtime Error") as ProblemRunCase["status"],
    runtime: String(c.runtime ?? "0ms"),
    memory: String(c.memory ?? "0.0MB"),
    runtimeMs: typeof c.runtimeMs === "number" ? c.runtimeMs : undefined,
    memoryMb: typeof c.memoryMb === "number" ? c.memoryMb : undefined,
    detail: c.detail as string | undefined,
    output: c.output as string | undefined,
    expectedOutput: c.expectedOutput as string | undefined,
    inputs: Array.isArray(c.inputs)
      ? (c.inputs as ProblemRunCase["inputs"])
      : undefined,
  };
}

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
