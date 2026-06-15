import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}));

import { apiGet, apiPost } from "@/utils/request";
import {
  mapSubmission,
  mapRunResult,
  mapDistributionBins,
  fetchProblemSubmissions,
  fetchUserSubmissions,
  fetchSubmissionStatuses,
  createSubmission,
  runSubmission,
  fetchDailyActivity,
} from "@/api/submission";

beforeEach(() => {
  vi.clearAllMocks();
});

describe("mapDistributionBins", () => {
  it("returns number[] for number[] input", () => {
    expect(mapDistributionBins([8, 16, 32])).toEqual([8, 16, 32]);
  });

  it("parses JSON string to number[] (legacy backend shape)", () => {
    expect(mapDistributionBins("[8, 16, 32]")).toEqual([8, 16, 32]);
  });

  it("returns [] for null/undefined/non-array/non-string", () => {
    expect(mapDistributionBins(null)).toEqual([]);
    expect(mapDistributionBins(undefined)).toEqual([]);
    expect(mapDistributionBins("not json")).toEqual([]);
    expect(mapDistributionBins({ foo: 1 })).toEqual([]);
    expect(mapDistributionBins(123)).toEqual([]);
  });

  it("filters non-number values from parsed array", () => {
    expect(mapDistributionBins('[8, "x", 16, null, 32]')).toEqual([8, 16, 32]);
  });

  it("returns [] for empty array", () => {
    expect(mapDistributionBins([])).toEqual([]);
  });
});

describe("mapSubmission", () => {
  it("maps snake_case to camelCase (legacy backend)", () => {
    const result = mapSubmission({
      id: "s1",
      problem_id: 1,
      created_at: "2026-06-10T00:00:00",
      runtime_percentile: 75.0,
      memory_percentile: 50.0,
      error_detail: "x",
    });
    expect(result.id).toBe("s1");
    expect(result.problem_id).toBe(1);
    expect(result.created_at).toBe("2026-06-10T00:00:00");
    expect(result.runtimePercentile).toBe(75.0);
    expect(result.memoryPercentile).toBe(50.0);
    expect(result.errorDetail).toBe("x");
  });

  it("passes through camelCase keys (v2 backend)", () => {
    const result = mapSubmission({
      id: "s1",
      problem_id: 1,
      createdAt: "2026-06-10T00:00:00",
      runtimePercentile: 75.0,
    });
    expect(result.created_at).toBe("2026-06-10T00:00:00");
    expect(result.runtimePercentile).toBe(75.0);
  });

  it("normalizes memoryDistBinsMb from string (legacy backend bug)", () => {
    const result = mapSubmission({ memory_dist_bins_mb: "[8, 16, 32]" });
    expect(result.memoryDistBinsMb).toEqual([8, 16, 32]);
  });

  it("normalizes runtimeDistBinsMs from array (v2 backend)", () => {
    const result = mapSubmission({ runtime_dist_bins_ms: [100, 200, 300] });
    expect(result.runtimeDistBinsMs).toEqual([100, 200, 300]);
  });

  it("handles null sub gracefully", () => {
    expect(mapSubmission(null)).toBeNull();
  });

  it("aliases submittedAt from created_at when missing", () => {
    const result = mapSubmission({
      id: "s1",
      created_at: "2026-06-10T00:00:00",
    });
    expect(result.submittedAt).toBe("2026-06-10T00:00:00");
  });
});

describe("mapRunResult", () => {
  it("maps v2 schema with numeric runtimeMs/memoryMb", () => {
    const r = mapRunResult({
      id: "r1",
      problemId: 1,
      userId: "u1",
      verdict: "Accepted",
      runtime: "12ms",
      runtimeMs: 12,
      memory: "22.0MB",
      memoryMb: 22.0,
      cases: [],
      passedCases: 1,
      totalCases: 1,
    });
    expect(r.problemId).toBe(1);
    expect(r.runtimeMs).toBe(12);
    expect(r.memoryMb).toBe(22.0);
    expect(r.verdict).toBe("Accepted");
    expect(r.passed_cases).toBe(1);
    expect(r.total_cases).toBe(1);
  });

  it("falls back to old string problemId for legacy compatibility", () => {
    // Legacy backend: problemId: "1" (string)
    const r = mapRunResult({ problemId: "1", cases: [] });
    expect(r.problemId).toBe(1); // forced to Number
  });

  it("handles missing numeric fields gracefully", () => {
    const r = mapRunResult({
      id: "r1",
      problemId: 1,
      verdict: "Runtime Error",
      runtime: "0ms",
      memory: "0.0MB",
      cases: [],
    });
    expect(r.runtimeMs).toBeUndefined();
    expect(r.memoryMb).toBeUndefined();
    expect(r.verdict).toBe("Runtime Error");
  });

  it("maps nested cases with default runtime/memory", () => {
    const r = mapRunResult({
      id: "r1",
      problemId: 1,
      cases: [
        { id: "c1", status: "Accepted", runtime: "10ms", memory: "5.0MB" },
        { id: "c2", status: "Wrong Answer" }, // missing runtime/memory
      ],
    });
    expect(r.cases).toHaveLength(2);
    expect(r.cases[1].runtime).toBe("0ms");
    expect(r.cases[1].memory).toBe("0.0MB");
    expect(r.cases[1].status).toBe("Wrong Answer");
  });

  it("maps snake_case fallback (passed_cases/total_cases)", () => {
    const r = mapRunResult({
      id: "r1",
      problemId: 1,
      cases: [],
      passed_cases: 3,
      total_cases: 5,
    });
    expect(r.passed_cases).toBe(3);
    expect(r.total_cases).toBe(5);
  });

  it("handles null input gracefully", () => {
    expect(mapRunResult(null)).toBeNull();
  });

  it("preserves per-case numeric runtimeMs/memoryMb when present", () => {
    const r = mapRunResult({
      id: "r1",
      problemId: 1,
      cases: [
        {
          id: "c1",
          status: "Accepted",
          runtime: "12ms",
          runtimeMs: 12,
          memory: "5.0MB",
          memoryMb: 5.0,
        },
      ],
    });
    expect(r.cases[0].runtimeMs).toBe(12);
    expect(r.cases[0].memoryMb).toBe(5.0);
  });
});

describe("submission fetchers", () => {
  it("fetchProblemSubmissions unwraps pageResult.items and maps each", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce({
      items: [
        { id: "s1", problem_id: 1 },
        { id: "s2", problem_id: 1 },
      ],
      total: 2,
      page: 1,
      pageSize: 10,
      totalPages: 1,
    });
    const result = await fetchProblemSubmissions(1);
    expect(result).toHaveLength(2);
    expect(result[0].id).toBe("s1");
    expect(result[1].id).toBe("s2");
    expect(apiGet).toHaveBeenCalledWith("/problems/1/submissions");
  });

  it("fetchUserSubmissions also unwraps pageResult", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce({
      items: [],
      total: 0,
      page: 1,
      pageSize: 10,
      totalPages: 0,
    });
    const result = await fetchUserSubmissions();
    expect(result).toEqual([]);
  });

  it("fetchSubmissionStatuses maps each item including snake_case fallback", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce([
      { key: "Accepted", code: "ACCEPTED", is_terminal: true, sort_order: 2 },
      { key: "Pending", code: "PENDING", isTerminal: false, sortOrder: 0 },
    ]);
    const result = await fetchSubmissionStatuses();
    expect(result).toHaveLength(2);
    expect(result[0].isTerminal).toBe(true);
    expect(result[0].sortOrder).toBe(2);
    expect(result[1].isTerminal).toBe(false);
    expect(result[1].sortOrder).toBe(0);
  });

  it("createSubmission sends POST and returns mapped record", async () => {
    vi.mocked(apiPost).mockResolvedValueOnce({
      id: "s1",
      problem_id: 1,
      status: "Pending",
    });
    const result = await createSubmission(1, {
      language: "javascript",
      code: "function solution(){}",
    });
    expect(result.id).toBe("s1");
    expect(result.problem_id).toBe(1);
    expect(apiPost).toHaveBeenCalledWith("/problems/1/submissions", {
      language: "javascript",
      code: "function solution(){}",
    });
  });

  it("runSubmission sends POST and returns mapped run result (v2)", async () => {
    vi.mocked(apiPost).mockResolvedValueOnce({
      id: "r1",
      problemId: 1, // numeric since v2
      verdict: "Accepted",
      runtime: "10ms",
      runtimeMs: 10,
      memory: "5.0MB",
      memoryMb: 5.0,
      cases: [],
      passedCases: 1,
      totalCases: 1,
    });
    const result = await runSubmission(1, {
      language: "javascript",
      code: "function solution(){}",
    });
    expect(result.problemId).toBe(1);
    expect(result.runtimeMs).toBe(10);
    expect(result.memoryMb).toBe(5.0);
    expect(result.verdict).toBe("Accepted");
  });

  it("runSubmission normalizes testCases (drops empty inputs)", async () => {
    vi.mocked(apiPost).mockResolvedValueOnce({
      id: "r1",
      problemId: 1,
      cases: [],
    });
    await runSubmission(1, {
      language: "javascript",
      code: "x",
      testCases: [
        { id: "t1", inputs: [{ name: "a", value: "1" }] },
        { id: "t2", inputs: [] }, // empty
      ],
    });
    const callBody = vi.mocked(apiPost).mock.calls[0][1] as {
      testCases: unknown[];
    };
    expect(callBody.testCases).toHaveLength(2); // both kept; empty inputs become undefined
  });

  it("fetchDailyActivity builds query string with year", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce(["2026-06-01", "2026-06-02"]);
    await fetchDailyActivity(2026);
    expect(apiGet).toHaveBeenCalledWith("/submissions/calendar?year=2026");
  });

  it("fetchDailyActivity omits year query when not provided", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce([]);
    await fetchDailyActivity();
    expect(apiGet).toHaveBeenCalledWith("/submissions/calendar");
  });
});
