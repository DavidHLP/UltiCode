import { describe, it, expect } from "vitest";
import {
  decodeProfile,
  mapDistributionBins,
  mapRunResult,
  mapSubmission,
  mapSubmissionStatus,
  readBool,
  readField,
  readNumber,
  readString,
} from "@/api/projection";

describe("readField / readNumber / readString / readBool", () => {
  it("prefers camelCase over snake_case", () => {
    expect(readField<string>({ a: "camel", a_b: "snake" }, "a", "a_b")).toBe(
      "camel",
    );
  });

  it("falls back to snake_case when camelCase is missing", () => {
    expect(readField<string>({ a_b: "snake" }, "a", "a_b")).toBe("snake");
  });

  it("returns fallback when both keys are missing", () => {
    expect(readField<string>({}, "a", "a_b", "fb")).toBe("fb");
    expect(readField<number>({}, "a", "a_b")).toBeUndefined();
  });

  it("treats null as missing", () => {
    expect(readField<string>({ a: null, a_b: "snake" }, "a", "a_b")).toBe(
      "snake",
    );
  });

  it("returns fallback for non-object input", () => {
    expect(readField<string>(null, "a", "a_b", "fb")).toBe("fb");
    expect(readField<string>("x", "a", "a_b", "fb")).toBe("fb");
  });

  it("readNumber coerces numeric strings and honors fallback", () => {
    expect(readNumber({ x: 1 }, "x", "x_y", 9)).toBe(1);
    expect(readNumber({ x_y: "42" }, "x", "x_y", 9)).toBe(42);
    expect(readNumber({ x: "abc" }, "x", "x_y", 9)).toBe(9);
    expect(readNumber({}, "x", "x_y", 9)).toBe(9);
  });

  it("readNumber treats numeric NaN as missing (no NaN leak)", () => {
    expect(readNumber({ x: NaN }, "x", "x_y", 9)).toBe(9);
    expect(readNumber({ x: NaN }, "x", "x_y")).toBeUndefined();
  });

  it("readString stringifies non-string values", () => {
    expect(readString({ x: 1 }, "x", "x_y", "fb")).toBe("1");
    expect(readString({ x: undefined }, "x", "x_y", "fb")).toBe("fb");
  });

  it("readBool defaults to false and honors true-ish values", () => {
    expect(readBool({}, "x", "x_y")).toBe(false);
    expect(readBool({ x: 1 }, "x", "x_y", true)).toBe(true);
    expect(readBool({ x: "yes" }, "x", "x_y")).toBe(true);
  });
});

describe("mapDistributionBins", () => {
  it("returns number[] for number[] input", () => {
    expect(mapDistributionBins([8, 16, 32])).toEqual([8, 16, 32]);
  });

  it("returns [] for null/undefined/non-array/non-string", () => {
    expect(mapDistributionBins(null)).toEqual([]);
    expect(mapDistributionBins(undefined)).toEqual([]);
    expect(mapDistributionBins("not json")).toEqual([]);
    expect(mapDistributionBins({ foo: 1 })).toEqual([]);
    expect(mapDistributionBins(123)).toEqual([]);
  });
});

describe("mapSubmission", () => {
  it("snake-only input maps to camelCase fields", () => {
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

  it("camel-only input passes through", () => {
    const result = mapSubmission({
      id: "s1",
      problem_id: 1,
      createdAt: "2026-06-10T00:00:00",
      runtimePercentile: 75.0,
    });
    expect(result.created_at).toBe("2026-06-10T00:00:00");
    expect(result.runtimePercentile).toBe(75.0);
  });

  it("normalizes dist bins from legacy JSON string", () => {
    const result = mapSubmission({ memory_dist_bins_mb: "[8, 16, 32]" });
    expect(result.memoryDistBinsMb).toEqual([8, 16, 32]);
  });

  it("aliases submittedAt from created_at when submittedAt is missing", () => {
    const result = mapSubmission({
      id: "s1",
      created_at: "2026-06-10T00:00:00",
    });
    expect(result.submittedAt).toBe("2026-06-10T00:00:00");
  });

  it("returns null on null input", () => {
    expect(mapSubmission(null)).toBeNull();
  });

  it("uses fallback defaults when both keys are missing", () => {
    const result = mapSubmission({ id: "s2" });
    expect(result.created_at).toBe("");
    expect(result.errorDetail).toBeUndefined();
    expect(result.runtimePercentile).toBeUndefined();
    expect(result.memoryPercentile).toBeUndefined();
  });
});

describe("mapSubmissionStatus", () => {
  it("maps snake_case is_terminal/sort_order fallback", () => {
    const result = mapSubmissionStatus({
      key: "Accepted",
      code: "ACCEPTED",
      is_terminal: true,
      sort_order: 2,
    });
    expect(result.isTerminal).toBe(true);
    expect(result.sortOrder).toBe(2);
  });

  it("maps camelCase isTerminal/sortOrder", () => {
    const result = mapSubmissionStatus({
      key: "Pending",
      code: "PENDING",
      isTerminal: false,
      sortOrder: 0,
    });
    expect(result.isTerminal).toBe(false);
    expect(result.sortOrder).toBe(0);
  });

  it("defaults sortOrder to 0 when both keys are missing", () => {
    const result = mapSubmissionStatus({
      key: "Pending",
      code: "PENDING",
    });
    expect(result.sortOrder).toBe(0);
    expect(result.isTerminal).toBe(false);
  });
});

describe("mapRunResult", () => {
  it("v2 schema with numeric runtimeMs/memoryMb maps cleanly", () => {
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

  it("snake_case fallback (passed_cases/total_cases) maps correctly", () => {
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

  it("returns null on null input", () => {
    expect(mapRunResult(null)).toBeNull();
  });

  it("coerces string passedCases to number", () => {
    const r = mapRunResult({
      id: "r1",
      problemId: 1,
      cases: [],
      passedCases: "4",
    });
    expect(r.passed_cases).toBe(4);
  });

  it("defaults passed_cases/total_cases to 0 when missing", () => {
    const r = mapRunResult({ id: "r1", problemId: 1, cases: [] });
    expect(r.passed_cases).toBe(0);
    expect(r.total_cases).toBe(0);
  });
});

describe("decodeProfile", () => {
  it("snake_case UserVO maps to camelCase ProfileData", () => {
    const r = decodeProfile({
      id: "u1",
      username: "alice",
      name: "Alice",
      avatar: "a.png",
      bio: "hi",
      joined_at: "2026-01-01",
      solved_count: 42,
      submission_count: 100,
    });
    expect(r.id).toBe("u1");
    expect(r.joinedAt).toBe("2026-01-01");
    expect(r.totalSolved).toBe(42);
    expect(r.submissionCount).toBe(100);
  });

  it("camelCase input passes through", () => {
    const r = decodeProfile({
      id: "u1",
      username: "alice",
      name: "Alice",
      avatar: "a.png",
      joinedAt: "2026-01-01",
      totalSolved: 9,
      submissionCount: 11,
    });
    expect(r.joinedAt).toBe("2026-01-01");
    expect(r.totalSolved).toBe(9);
    expect(r.submissionCount).toBe(11);
  });

  it("missing fields fall back to ProfileData defaults", () => {
    const r = decodeProfile({});
    expect(r.id).toBe("");
    expect(r.joinedAt).toBe("");
    expect(r.totalSolved).toBe(0);
    expect(r.submissionCount).toBe(0);
    expect(r.globalRank).toBeNull();
    expect(r.acceptanceRate).toBeNull();
  });

  it("returns empty ProfileData for null input", () => {
    const r = decodeProfile(null);
    expect(r.id).toBe("");
    expect(r.totalSolved).toBe(0);
  });

  it("maps UserVO `rank` to globalRank", () => {
    const r = decodeProfile({
      id: "u1",
      username: "alice",
      name: "Alice",
      avatar: "a.png",
      rank: 7,
    });
    expect(r.globalRank).toBe(7);
  });

  it("maps UserVO contact fields (email/twitter/github)", () => {
    const r = decodeProfile({
      id: "u1",
      username: "alice",
      name: "Alice",
      avatar: "a.png",
      email: "a@x.com",
      twitter: "https://x.com/a",
      github: "https://github.com/a",
    });
    expect(r.email).toBe("a@x.com");
    expect(r.twitter).toBe("https://x.com/a");
    expect(r.github).toBe("https://github.com/a");
  });

  it("empty contact fields default to ''", () => {
    const r = decodeProfile({});
    expect(r.email).toBe("");
    expect(r.twitter).toBe("");
    expect(r.github).toBe("");
  });
});
