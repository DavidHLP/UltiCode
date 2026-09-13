import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/utils/request", () => ({
  apiDelete: vi.fn(),
  apiGet: vi.fn(),
  apiPatch: vi.fn(),
  apiPost: vi.fn(),
}));

import { apiGet } from "@/utils/request";
import {
  fetchCommunityPosts,
  fetchForumPosts,
  fetchMyForumPosts,
} from "@/api/forum";

const post = {
  id: "post-1",
  userId: "user-1",
  title: "A post",
  tags: [],
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe("forum list fetchers", () => {
  it("maps the forum posts envelope to PageResult items", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce({
      posts: [post],
      total: 3,
      totalPages: 3,
    });

    const result = await fetchForumPosts({
      sortBy: "hot",
      page: 2,
      pageSize: 1,
    });

    expect(result.items[0]?.id).toBe("post-1");
    expect(result.total).toBe(3);
    expect(result.page).toBe(1);
    expect(result.pageSize).toBe(1);
    expect(result.totalPages).toBe(3);
    expect(apiGet).toHaveBeenCalledWith("/forum/posts", {
      params: { sortBy: "hot", page: 2, pageSize: 1 },
    });
  });

  it("wraps bare arrays for community and personal post lists", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce([post]);
    const communityResult = await fetchCommunityPosts("general");

    vi.mocked(apiGet).mockResolvedValueOnce([post]);
    const personalResult = await fetchMyForumPosts();

    expect(communityResult.items).toHaveLength(1);
    expect(personalResult.items).toHaveLength(1);
    expect(communityResult.totalPages).toBe(1);
    expect(personalResult.totalPages).toBe(1);
  });
});
