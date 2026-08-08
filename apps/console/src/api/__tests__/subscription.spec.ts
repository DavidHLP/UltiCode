import { describe, it, expect, vi, beforeEach } from "vitest";
import { apiGet, apiPost } from "@/utils/request";
import { subscriptionApi } from "@/api/subscription";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}));

const mockCheckResult = {
  hasAccess: true,
  subscription: {
    plan: "PREMIUM_MONTHLY",
    status: "ACTIVE",
    expiresAt: "2026-07-11T00:00:00",
  },
};

const mockCancelResult = {
  message: "Subscription will be cancelled at period end",
  cancelAt: "2026-07-11T00:00:00",
};

describe("subscriptionApi.getMySubscription", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("hits /subscriptions/check-premium (not /subscriptions/me)", async () => {
    vi.mocked(apiGet).mockResolvedValue(mockCheckResult);

    const result = await subscriptionApi.getMySubscription();

    expect(apiGet).toHaveBeenCalledTimes(1);
    expect(apiGet).toHaveBeenCalledWith("/subscriptions/check-premium");
    expect(result).toEqual(mockCheckResult);
  });

  it("returns the hasAccess + subscription shape from the backend", async () => {
    vi.mocked(apiGet).mockResolvedValue(mockCheckResult);

    const result = await subscriptionApi.getMySubscription();

    expect(result.hasAccess).toBe(true);
    expect(result.subscription?.plan).toBe("PREMIUM_MONTHLY");
    expect(result.subscription?.status).toBe("ACTIVE");
  });

  it("returns null subscription when user has no active subscription", async () => {
    vi.mocked(apiGet).mockResolvedValue({
      hasAccess: false,
      subscription: null,
    });

    const result = await subscriptionApi.getMySubscription();

    expect(result.hasAccess).toBe(false);
    expect(result.subscription).toBeNull();
  });
});

describe("subscriptionApi.cancelSubscription", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("uses POST /subscriptions/{id}/cancel with the provided id", async () => {
    vi.mocked(apiPost).mockResolvedValue(mockCancelResult);

    const result = await subscriptionApi.cancelSubscription("sub-abc-123");

    expect(apiPost).toHaveBeenCalledTimes(1);
    expect(apiPost).toHaveBeenCalledWith(
      "/subscriptions/sub-abc-123/cancel",
      {},
    );
    expect(result).toEqual(mockCancelResult);
  });

  it("does NOT call the legacy /subscriptions/cancel path (no id)", async () => {
    vi.mocked(apiPost).mockResolvedValue(mockCancelResult);

    await subscriptionApi.cancelSubscription("sub-abc-123");

    const calledUrl = vi.mocked(apiPost).mock.calls[0]?.[0] as string;
    expect(calledUrl).not.toBe("/subscriptions/cancel");
    expect(calledUrl).toMatch(/^\/subscriptions\/[^/]+\/cancel$/);
  });
});
