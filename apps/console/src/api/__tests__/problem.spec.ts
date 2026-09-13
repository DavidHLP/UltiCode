import { describe, it, expect, vi, beforeEach } from "vitest";
import { apiGet } from "@/utils/request";
import { fetchProblems, mapProblem } from "@/api/problem";
import type { Problem } from "@/types/problem";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
});

const baseSnake = {
  id: "1",
  title: "Two Sum",
  slug: "two-sum",
  difficulty: "EASY",
  acceptance_rate: "42.5",
  is_premium: true,
  has_solution: false,
  completed_time: "2026-01-01T00:00:00",
  tags: ["array"],
};

/**
 * Direct mapProblem seam tests. The candidate's premise was that two wire
 * shapes (snake_case + camelCase) leak through every seam; these tests lock
 * the single locality (mapProblem → readField/readNumber) that collapses the
 * duality, and pin the dual-key OUTPUT shape (both acceptance_rate and
 * acceptanceRate) consumers depend on.
 */
describe("mapProblem", () => {
  it("resolves snake_case fields to the camelCase Problem shape", () => {
    const p = mapProblem({ ...baseSnake }) as Problem;
    expect(p.id).toBe(1);
    expect(p.acceptanceRate).toBe(42.5);
    // dual-key output preserved for consumers that still read snake_case
    expect(p.acceptance_rate).toBe(42.5);
    expect(p.isPremium).toBe(true);
    expect(p.hasSolution).toBe(false);
    expect(p.completedTime).toBe("2026-01-01T00:00:00");
  });

  it("keeps working when the wire already uses camelCase", () => {
    const p = mapProblem({
      id: 7,
      title: "X",
      slug: "x",
      difficulty: "HARD",
      acceptanceRate: 9,
      isPremium: false,
      hasSolution: true,
      completedTime: "2026-02-02T00:00:00",
      tags: [],
    }) as Problem;
    expect(p.id).toBe(7);
    expect(p.acceptanceRate).toBe(9);
    expect(p.acceptance_rate).toBe(9);
    expect(p.isPremium).toBe(false);
    expect(p.hasSolution).toBe(true);
    expect(p.completedTime).toBe("2026-02-02T00:00:00");
  });

  it("rejects an unparseable acceptanceRate at the API boundary", () => {
    expect(() => mapProblem({ ...baseSnake, acceptance_rate: "abc" })).toThrow(
      "acceptanceRate",
    );
  });

  it("rejects non-object input at the API boundary", () => {
    expect(() => mapProblem(null as unknown)).toThrow("must be an object");
    expect(() => mapProblem(undefined as unknown)).toThrow("must be an object");
  });
});

describe("fetchProblems", () => {
  it("returns mapped items with the canonical page envelope", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce({
      items: [{ ...baseSnake }],
      total: 4,
      page: 2,
      pageSize: 1,
      totalPages: 4,
    });

    const result = await fetchProblems({ search: "two sum" }, 2, 1);

    expect(result.items[0]?.title).toBe("Two Sum");
    expect(result.total).toBe(4);
    expect(result.page).toBe(2);
    expect(result.pageSize).toBe(1);
    expect(result.totalPages).toBe(4);
    expect(apiGet).toHaveBeenCalledWith(
      "/problems?page=2&pageSize=1&search=two+sum",
    );
  });
});
