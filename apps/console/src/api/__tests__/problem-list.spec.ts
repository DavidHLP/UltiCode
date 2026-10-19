import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiGet } from "@/utils/request";
import { fetchProblemListOverview } from "@/api/problem-list";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiDelete: vi.fn(),
  apiPatch: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());

const problem = {
  id: 1,
  title: "Two Sum",
  slug: "two-sum",
  difficulty: "EASY",
  acceptanceRate: 42.5,
  tags: [],
};

describe("fetchProblemListOverview", () => {
  it("rejects detail items without category slugs", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce({
      id: "list-1",
      name: "Essentials",
      problems: [problem],
    });

    await expect(fetchProblemListOverview("list-1")).rejects.toThrow(
      "categorySlugs",
    );
  });

  it("preserves category slugs on detail items", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce({
      id: "list-1",
      name: "Essentials",
      problems: [{ ...problem, categorySlugs: ["algorithms"] }],
    });

    const result = await fetchProblemListOverview("list-1");

    expect(result.problems[0]?.categorySlugs).toEqual(["algorithms"]);
  });
});
