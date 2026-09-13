import { describe, it, expect, vi, beforeEach } from "vitest";
import { apiGet, apiPost } from "@/utils/request";
import { fetchUserContests, finishVirtualContest } from "@/api/contest";

vi.mock("@/utils/request", () => ({
  apiPost: vi.fn(),
  apiGet: vi.fn(),
}));

const contest = {
  id: "contest-1",
  slug: "contest-1",
  title: "Contest 1",
  status: "UPCOMING",
  startTime: "2026-09-12T09:00:00Z",
  duration: 60,
  contestType: "ICPC",
  scoringMode: "ICPC",
};

describe("finishVirtualContest (R10.1 / F-51)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("sends sessionId as a query param (matches backend @RequestParam contract)", async () => {
    vi.mocked(apiPost).mockResolvedValue(undefined);

    await finishVirtualContest("contest-upcoming-002", "7de57898-eb3c-4db0-bb8f-3dd1d60fcdb2");

    // The previous body-based shape (`{ sessionId }`) silently failed on
    // the backend because Spring's @RequestParam doesn't look at the
    // request body. The service compensated via participant.getVirtualSessionId()
    // but that masks stale-cache bugs (F-51). Send it in the URL.
    expect(apiPost).toHaveBeenCalledTimes(1);
    const [url, body] = vi.mocked(apiPost).mock.calls[0]!;
    expect(url).toBe(
      "/contest/contest-upcoming-002/virtual/finish?sessionId=7de57898-eb3c-4db0-bb8f-3dd1d60fcdb2",
    );
    expect(body).toBeUndefined();
  });

  it("URL-encodes special characters in the sessionId", async () => {
    vi.mocked(apiPost).mockResolvedValue(undefined);

    // UUIDs don't contain reserved chars, but the helper covers future
    // formats (e.g. JWT-like tokens with dots / slashes).
    await finishVirtualContest("contest-x", "abc/def+ghi=");

    const [url] = vi.mocked(apiPost).mock.calls[0]!;
    expect(url).toBe(
      "/contest/contest-x/virtual/finish?sessionId=abc%2Fdef%2Bghi%3D",
    );
  });

  it("propagates errors from apiPost so the store can surface them", async () => {
    const networkError = new Error("Network down");
    vi.mocked(apiPost).mockRejectedValue(networkError);

    await expect(finishVirtualContest("c1", "s1")).rejects.toBe(networkError);
  });
});

describe("fetchUserContests (C10)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("normalizes a paginated object envelope to PageResult", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce({
      items: [contest],
      total: 3,
      page: 2,
      pageSize: 1,
      totalPages: 3,
    });

    const result = await fetchUserContests("registered");

    expect(result.items[0]?.id).toBe("contest-1");
    expect(result.total).toBe(3);
    expect(result.page).toBe(2);
    expect(result.pageSize).toBe(1);
    expect(result.totalPages).toBe(3);
    expect(apiGet).toHaveBeenCalledWith("/contest/user/my-contests", {
      params: { type: "registered" },
    });
  });

  it("wraps legacy arrays, including an empty page", async () => {
    vi.mocked(apiGet).mockResolvedValueOnce([]);

    await expect(fetchUserContests("virtual")).resolves.toEqual({
      items: [],
      total: 0,
      page: 1,
      pageSize: 0,
      totalPages: 0,
    });
  });
});
