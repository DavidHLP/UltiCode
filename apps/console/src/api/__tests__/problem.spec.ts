import { describe, it, expect } from "vitest";
import { mapProblem } from "@/api/problem";
import type { Problem } from "@/types/problem";

/**
 * Direct mapProblem seam tests. The candidate's premise was that two wire
 * shapes (snake_case + camelCase) leak through every seam; these tests lock
 * the single locality (mapProblem → readField/readNumber) that collapses the
 * duality, and pin the dual-key OUTPUT shape (both acceptance_rate and
 * acceptanceRate) consumers depend on.
 */
describe("mapProblem", () => {
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

  it("returns undefined acceptanceRate for an unparseable value", () => {
    const p = mapProblem({ ...baseSnake, acceptance_rate: "abc" }) as Problem;
    expect(p.acceptanceRate).toBeUndefined();
    expect(p.acceptance_rate).toBeUndefined();
  });

  it("returns the input untouched for non-object input", () => {
    expect(mapProblem(null as unknown)).toBeNull();
    expect(mapProblem(undefined as unknown)).toBeUndefined();
  });
});
