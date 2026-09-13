import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/utils/request", () => ({
  apiDelete: vi.fn(),
  apiGet: vi.fn(),
  apiPatch: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
}));

import { apiGet } from "@/utils/request";
import { fetchSolutionFeed, fetchUserSolutions } from "@/api/solution";

const listItem = {
  id: "solution-1",
  problemId: 1,
  title: "A solution",
  summary: "Summary",
  language: "java",
  tags: [],
  author: { id: "user-1", name: "User" },
  counts: { views: 1, comments: 0, likes: 2, dislikes: 0 },
  score: 2,
  publishedAt: "2026-01-01T00:00:00Z",
};

const detailItem = {
  id: "solution-1",
  problemId: 1,
  userId: "user-1",
  title: "A solution",
  summary: "Summary",
  language: "java",
  tags: [],
  score: 2,
  publishedAt: "2026-01-01T00:00:00Z",
  createdAt: "2026-01-01T00:00:00Z",
  content: "body",
  votes: 2,
  views: 1,
  likes: 2,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("solution list fetchers", () => {
  it("maps a paginated solution feed to PageResult", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce({
      items: [listItem],
      total: 4,
      page: 2,
      pageSize: 1,
      totalPages: 4,
    });

    const result = await fetchSolutionFeed(1);

    expect(result.items[0]?.id).toBe("solution-1");
    expect(result.total).toBe(4);
    expect(result.page).toBe(2);
    expect(result.pageSize).toBe(1);
    expect(result.totalPages).toBe(4);
  });

  it("wraps the user solution bare-array response", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce([detailItem]);

    const result = await fetchUserSolutions("user-1");

    expect(result.items[0]?.id).toBe("solution-1");
    expect(result.total).toBe(1);
    expect(result.page).toBe(1);
    expect(result.totalPages).toBe(1);
    expect(apiGet).toHaveBeenCalledWith("/api/solutions?userId=user-1");
  });
});
