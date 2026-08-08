import { describe, it, expect, vi, beforeEach } from "vitest";
import { apiPost } from "@/utils/request";
import { finishVirtualContest } from "@/api/contest";

vi.mock("@/utils/request", () => ({
  apiPost: vi.fn(),
  apiGet: vi.fn(),
}));

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
