import { describe, it, expect, vi, beforeEach } from "vitest";
import { apiGet } from "@/utils/request";
import { fetchProblemDetailById, mapProblemDetail } from "@/api/problem-detail";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
}));

vi.mock("@/api/problem", () => ({
  mapProblem: vi.fn(() => ({
    id: 1,
    title: "Test Problem",
    slug: "test-problem",
    difficulty: "EASY",
  })),
}));

const mockBackendResponse = {
  detail: {
    summary: "A test problem description",
    companies: null,
    constraints_json: ["1 <= n <= 100"],
    follow_up: "",
    hints: null,
  },
  examples: [],
  languages: [],
};

describe("fetchProblemDetailById", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(apiGet).mockResolvedValue(mockBackendResponse);
  });

  it("routes numeric ID to /problems/{id}", async () => {
    await fetchProblemDetailById(1);

    expect(apiGet).toHaveBeenCalledWith("/problems/1");
  });

  it("routes string slug to /problems/slug/{slug}", async () => {
    await fetchProblemDetailById("two-sum");

    expect(apiGet).toHaveBeenCalledWith("/problems/slug/two-sum");
  });

  it("routes numeric string ID to /problems/{id}", async () => {
    await fetchProblemDetailById("42");

    expect(apiGet).toHaveBeenCalledWith("/problems/42");
  });

  it("appends userId query parameter when provided", async () => {
    await fetchProblemDetailById(1, "user-123");

    expect(apiGet).toHaveBeenCalledWith("/problems/1?userId=user-123");
  });

  it("does not append userId when not provided", async () => {
    await fetchProblemDetailById(1);

    expect(apiGet).toHaveBeenCalledWith("/problems/1");
  });

  it("appends userId with slug route", async () => {
    await fetchProblemDetailById("two-sum", "user-456");

    expect(apiGet).toHaveBeenCalledWith(
      "/problems/slug/two-sum?userId=user-456",
    );
  });

  it("maps structured example inputs into editable test case fields", () => {
    const problem = mapProblemDetail({
      ...mockBackendResponse,
      examples: [
        {
          id: "pe-001-1",
          input: "nums = [2,7,11,15], target = 9",
          output: "[0,1]",
          explanation: "nums[0] + nums[1] == 9",
          inputs: [
            { name: "nums", value: [2, 7, 11, 15] },
            { name: "target", value: 9 },
          ],
        },
      ],
    });

    expect(problem.testCases?.[0]?.inputs).toEqual([
      {
        id: "pe-001-1-input-0",
        name: "nums",
        fieldName: "nums",
        label: "nums",
        value: "[2,7,11,15]",
      },
      {
        id: "pe-001-1-input-1",
        name: "target",
        fieldName: "target",
        label: "target",
        value: "9",
      },
    ]);
  });

  it("falls back to parsing input when structured example inputs are missing", () => {
    const problem = mapProblemDetail({
      ...mockBackendResponse,
      examples: [
        {
          id: "pe-001-2",
          input: "nums = [3,2,4], target = 6",
          output: "[1,2]",
          explanation: "nums[1] + nums[2] == 6",
        },
      ],
    });

    expect(
      problem.testCases?.[0]?.inputs?.map((input) => ({
        name: input.name,
        value: input.value,
      })),
    ).toEqual([
      { name: "nums", value: "[3,2,4]" },
      { name: "target", value: "6" },
    ]);
  });

  it("keeps compatibility with legacy snake_case example fields", () => {
    const problem = mapProblemDetail({
      ...mockBackendResponse,
      examples: [
        {
          id: "legacy-example",
          input_text: "nums = [3,3], target = 6",
          output_text: "[0,1]",
          explanation: "legacy payload",
        },
      ],
    });

    expect(problem.examples).toEqual([
      {
        input: "nums = [3,3], target = 6",
        output: "[0,1]",
        explanation: "legacy payload",
      },
    ]);
  });

  // ----- D-10: per-user viewer reaction (backend injects via SecurityContextHolder) -----

  it("maps interactions.viewer.reaction when current user has reacted", () => {
    const problem = mapProblemDetail({
      ...mockBackendResponse,
      interactions: {
        likes: 12,
        dislikes: 1,
        favorites: 3,
        viewer: { reaction: "like" },
      },
    });
    expect(problem.interactions?.viewer?.reaction).toBe("like");
    expect(problem.interactions?.counts.likes).toBe(12);
  });

  it("returns viewer.reaction undefined when current user has not reacted", () => {
    const problem = mapProblemDetail({
      ...mockBackendResponse,
      interactions: {
        likes: 12,
        dislikes: 1,
        favorites: 3,
        viewer: { reaction: null },
      },
    });
    expect(problem.interactions?.viewer?.reaction).toBeUndefined();
  });

  it("routes userId through ?userId= query param to differentiate viewer", async () => {
    vi.mocked(apiGet)
      .mockResolvedValueOnce({
        ...mockBackendResponse,
        interactions: {
          likes: 1,
          dislikes: 0,
          favorites: 0,
          viewer: { reaction: "like" },
        },
      })
      .mockResolvedValueOnce({
        ...mockBackendResponse,
        interactions: {
          likes: 1,
          dislikes: 0,
          favorites: 0,
          viewer: { reaction: "dislike" },
        },
      });
    const a = await fetchProblemDetailById(1, "user-A");
    const b = await fetchProblemDetailById(1, "user-B");
    expect(a.interactions?.viewer?.reaction).toBe("like");
    expect(b.interactions?.viewer?.reaction).toBe("dislike");
    expect(vi.mocked(apiGet)).toHaveBeenNthCalledWith(
      1,
      "/problems/1?userId=user-A",
    );
    expect(vi.mocked(apiGet)).toHaveBeenNthCalledWith(
      2,
      "/problems/1?userId=user-B",
    );
  });
});
