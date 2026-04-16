import { describe, it, expect, vi, beforeEach } from "vitest";
import { apiGet } from "@/utils/request";
import { fetchProblemDetailById } from "@/api/problem-detail";

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
});
