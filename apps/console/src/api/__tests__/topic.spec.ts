import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiGet } from "@/utils/request";
import { fetchSolutionTopics } from "@/api/topic";

vi.mock("@/utils/request", () => ({ apiGet: vi.fn() }));

beforeEach(() => vi.clearAllMocks());

describe("fetchSolutionTopics", () => {
  it("maps the controller's topic array to the existing authoring contract", async () => {
    const topics = [{ id: "topic-dp", name: "动态规划", count: 5 }];
    vi.mocked(apiGet).mockResolvedValue(topics);
    await expect(fetchSolutionTopics()).resolves.toEqual({ topics });
    expect(apiGet).toHaveBeenCalledWith("/solution-topics");
  });

  it("preserves an empty topic list", async () => {
    vi.mocked(apiGet).mockResolvedValue([]);
    await expect(fetchSolutionTopics()).resolves.toEqual({ topics: [] });
  });

  it("propagates failures to the existing authoring error state", async () => {
    const error = new Error("Unavailable");
    vi.mocked(apiGet).mockRejectedValue(error);
    await expect(fetchSolutionTopics()).rejects.toBe(error);
  });
});
