/**
 * Projection seam for console API responses.
 *
 * Backend Spring Boot returns a mix of snake_case (legacy) and camelCase (v2)
 * fields for the same logical VO. Every decoder in this module collapses that
 * duality through the small `readField` / `readNumber` / `readString` /
 * `readBool` helpers below, so the public API surface always returns the
 * camelCase shape consumers already depend on.
 *
 * Two parallel projection styles coexist in the repo today:
 *   - this module (plain JS readers — used by submission, user)
 *   - `contest.schema.ts` (Zod parsers — used by contest)
 * The Zod system is intentionally left alone; it has its own validation
 * contract that does not collapse into these readers.
 */

import type {
  SubmissionRecord,
  SubmissionStatusMeta,
} from "@/types/submission";
import type { ProblemRunResult, ProblemRunCase } from "@/types/test-results";
import type { ProfileData, UserProfile } from "@/api/user";

// ============================================================================
// Shared readers
// ============================================================================

/**
 * Read a field from a raw object, preferring the camelCase key and falling
 * back to the snake_case key. Returns `fallback` when neither key is present
 * or the value is `null`/`undefined`.
 *
 * This is the single seam that kills the repeated `camel ?? snake` ternaries
 * that used to live inline in every map function.
 */
export function readField<T>(
  raw: unknown,
  camelKey: string,
  snakeKey: string,
  fallback?: T,
): T | undefined {
  if (!raw || typeof raw !== "object") return fallback;
  const obj = raw as Record<string, unknown>;
  const camel = obj[camelKey];
  if (camel !== undefined && camel !== null) return camel as T;
  const snake = obj[snakeKey];
  if (snake !== undefined && snake !== null) return snake as T;
  return fallback;
}

/** Read a number-coerced field. */
export function readNumber(
  raw: unknown,
  camelKey: string,
  snakeKey: string,
  fallback?: number,
): number | undefined {
  const v = readField<unknown>(raw, camelKey, snakeKey);
  if (v === undefined) return fallback;
  if (typeof v === "number") return v;
  if (typeof v === "string" && v.trim() !== "" && !Number.isNaN(Number(v))) {
    return Number(v);
  }
  return fallback;
}

/** Read a string field. */
export function readString(
  raw: unknown,
  camelKey: string,
  snakeKey: string,
  fallback?: string,
): string | undefined {
  const v = readField<unknown>(raw, camelKey, snakeKey);
  if (v === undefined) return fallback;
  return typeof v === "string" ? v : String(v);
}

/** Read a boolean field. `Boolean(undefined)` is `false`. */
export function readBool(
  raw: unknown,
  camelKey: string,
  snakeKey: string,
  fallback = false,
): boolean {
  const v = readField<unknown>(raw, camelKey, snakeKey);
  if (v === undefined || v === null) return fallback;
  return Boolean(v);
}

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

// ============================================================================
// Submission decoders
// ============================================================================

// Helper to map backend snake_case to frontend camelCase
export function mapSubmission(sub: unknown): SubmissionRecord {
  if (!sub || typeof sub !== "object") return sub as SubmissionRecord;
  const s = sub as Record<string, unknown>;
  return {
    ...(s as unknown as SubmissionRecord),
    created_at: readString(s, "createdAt", "created_at", "") as string,
    submittedAt: readString(
      s,
      "submittedAt",
      "submitted_at",
      readString(s, "createdAt", "created_at"),
    ),
    errorDetail: readString(s, "errorDetail", "error_detail"),
    runtimePercentile: readNumber(
      s,
      "runtimePercentile",
      "runtime_percentile",
    ),
    memoryPercentile: readNumber(
      s,
      "memoryPercentile",
      "memory_percentile",
    ),
    // v2 schema: backend returns number[]; helper still tolerates legacy JSON string.
    runtimeDistBinsMs: mapDistributionBins(
      readField(s, "runtimeDistBinsMs", "runtime_dist_bins_ms"),
    ),
    memoryDistBinsMb: mapDistributionBins(
      readField(s, "memoryDistBinsMb", "memory_dist_bins_mb"),
    ),
  } as SubmissionRecord;
}

export function mapSubmissionStatus(
  meta: unknown,
): SubmissionStatusMeta {
  if (!meta || typeof meta !== "object") return meta as SubmissionStatusMeta;
  const m = meta as Record<string, unknown>;
  return {
    key: m.key as SubmissionStatusMeta["key"],
    code: String(m.code ?? ""),
    label: String(m.label ?? ""),
    description: readString(m, "description", "description"),
    suggestion: readString(m, "suggestion", "suggestion"),
    category: m.category as SubmissionStatusMeta["category"],
    severity: m.severity as SubmissionStatusMeta["severity"],
    isTerminal: readBool(m, "isTerminal", "is_terminal"),
    sortOrder: readNumber(m, "sortOrder", "sort_order", 0) ?? 0,
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
  const r = raw as Record<string, unknown>;

  const cases = Array.isArray(r.cases)
    ? (r.cases as unknown[]).map(mapRunCase)
    : ([] as ProblemRunCase[]);

  return {
    id: String(r.id ?? ""),
    submissionId: String(r.id ?? ""),
    problemId: Number(r.problemId ?? 0),
    userId: String(r.userId ?? ""),
    verdict: String(
      r.verdict ?? "Runtime Error",
    ) as ProblemRunResult["verdict"],
    runtime: String(r.runtime ?? ""),
    memory: String(r.memory ?? ""),
    runtimeMs: typeof r.runtimeMs === "number" ? r.runtimeMs : undefined,
    memoryMb: typeof r.memoryMb === "number" ? r.memoryMb : undefined,
    cases,
    passed_cases: readNumber(r, "passedCases", "passed_cases", 0) ?? 0,
    total_cases: readNumber(r, "totalCases", "total_cases", 0) ?? 0,
    errorMessage: readString(r, "errorMessage", "error_message"),
  };
}

export function mapRunCase(raw: unknown): ProblemRunCase {
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
  const c = raw as Record<string, unknown>;
  return {
    id: String(c.id ?? ""),
    runId: String(c.runId ?? ""),
    submissionTestId: readString(c, "submissionTestId", "submission_test_id", "") as string,
    testCaseId: readString(c, "testCaseId", "test_case_id", "") as string,
    caseLabel: readString(c, "caseLabel", "case_label", "") as string,
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

// ============================================================================
// Profile decoder
// ============================================================================

/**
 * Decode a `UserProfile` (snake_case `UserVO`) into the canonical
 * camelCase `ProfileData` shape consumers rely on.
 *
 * <p>This keeps the public `ProfileData` interface stable while letting
 * `fetchUserProfile` go through the same projection seam as the rest of
 * the API. Only used by the projection tests / future callers — the
 * existing `fetchUserProfile` keeps its snake_case return type to avoid
 * breaking consumers.
 */
export function decodeProfile(raw: unknown): ProfileData {
  const empty: ProfileData = {
    id: "",
    username: "",
    name: "",
    avatar: "",
    bio: "",
    company: "",
    location: "",
    website: "",
    joinedAt: "",
    preferredLanguage: "",
    totalSolved: 0,
    submissionCount: 0,
    globalRank: null,
    acceptanceRate: null,
    followerCount: 0,
    followingCount: 0,
    achievementCount: 0,
  };
  if (!raw || typeof raw !== "object") return empty;
  const r = raw as Record<string, unknown> & Partial<UserProfile>;
  return {
    id: String(r.id ?? ""),
    username: String(r.username ?? ""),
    name: String(r.name ?? ""),
    avatar: String(r.avatar ?? ""),
    bio: String(r.bio ?? ""),
    company: String((r as Record<string, unknown>).company ?? ""),
    location: String(r.location ?? ""),
    website: String(r.website ?? ""),
    joinedAt: readString(r, "joinedAt", "joined_at", "") as string,
    preferredLanguage: String(
      (r as Record<string, unknown>).preferredLanguage ?? "",
    ),
    totalSolved: readNumber(r, "totalSolved", "solved_count", 0) ?? 0,
    submissionCount: readNumber(r, "submissionCount", "submission_count", 0) ?? 0,
    globalRank: readNumber(r, "globalRank", "global_rank") ?? null,
    acceptanceRate: readNumber(r, "acceptanceRate", "acceptance_rate") ?? null,
    followerCount: readNumber(r, "followerCount", "follower_count", 0) ?? 0,
    followingCount: readNumber(r, "followingCount", "following_count", 0) ?? 0,
    achievementCount: readNumber(r, "achievementCount", "achievement_count", 0) ?? 0,
  };
}
