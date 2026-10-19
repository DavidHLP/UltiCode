import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fetchGlobalRankings } from "@/api/contest";
import { useContestRankingStore } from "./contestRanking";
import type { GlobalRankingEntry, PaginatedResult } from "@/types/contest";

vi.mock("@/api/contest", () => ({
  fetchGlobalRankings: vi.fn(),
  fetchUserContestHistory: vi.fn(),
  fetchUserContests: vi.fn(),
}));
const entry = (rank: number) =>
  ({ rank, userId: `u${rank}`, username: `u${rank}` }) as GlobalRankingEntry;
const page = (number: number): PaginatedResult<GlobalRankingEntry> => ({
  items: [entry(number * 2 - 1), entry(number * 2)],
  total: 4,
  page: number,
  pageSize: 2,
  totalPages: 2,
});

describe("ranking pagination", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
  });

  it("appends the next page with the same country, retries failures and stops at the total", async () => {
    const store = useContestRankingStore();
    vi.mocked(fetchGlobalRankings).mockResolvedValueOnce(page(1));
    await store.loadGlobalRankings({ limit: 2, country: "CN" });
    expect(store.hasMoreRankings).toBe(true);
    vi.mocked(fetchGlobalRankings).mockRejectedValueOnce(
      new Error("unavailable"),
    );
    await expect(store.loadMoreGlobalRankings()).rejects.toThrow("unavailable");
    expect(store.globalRankings.map((r) => r.rank)).toEqual([1, 2]);
    expect(store.loadMoreRankingsError).toBe("unavailable");
    expect(store.error).toBeNull();
    vi.mocked(fetchGlobalRankings).mockResolvedValueOnce(page(2));
    await store.loadMoreGlobalRankings();
    expect(fetchGlobalRankings).toHaveBeenLastCalledWith({
      page: 2,
      limit: 2,
      country: "CN",
    });
    expect(store.globalRankings.map((r) => r.rank)).toEqual([1, 2, 3, 4]);
    expect(store.hasMoreRankings).toBe(false);
    expect(store.loadMoreRankingsError).toBeNull();
    await store.loadMoreGlobalRankings();
    expect(fetchGlobalRankings).toHaveBeenCalledTimes(3);
  });

  it("uses server page metadata when the requested page size is capped", async () => {
    const store = useContestRankingStore();
    vi.mocked(fetchGlobalRankings).mockResolvedValueOnce(page(1));
    await store.loadGlobalRankings({ limit: 10 });
    expect(store.hasMoreRankings).toBe(true);
    vi.mocked(fetchGlobalRankings).mockResolvedValueOnce(page(2));
    await store.loadMoreGlobalRankings();
    expect(fetchGlobalRankings).toHaveBeenLastCalledWith({
      page: 2,
      limit: 2,
      country: undefined,
    });
    expect(store.hasMoreRankings).toBe(false);
  });

  it("ignores duplicate clicks and discards an old country page after a scope change", async () => {
    const store = useContestRankingStore();
    vi.mocked(fetchGlobalRankings).mockResolvedValueOnce(page(1));
    await store.loadGlobalRankings({ limit: 2, country: "CN" });
    let resolve!: (value: PaginatedResult<GlobalRankingEntry>) => void;
    vi.mocked(fetchGlobalRankings).mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = r;
        }),
    );
    const pending = store.loadMoreGlobalRankings();
    await store.loadMoreGlobalRankings();
    expect(fetchGlobalRankings).toHaveBeenCalledTimes(2);
    vi.mocked(fetchGlobalRankings).mockResolvedValueOnce({
      items: [entry(9)],
      total: 1,
      page: 1,
      pageSize: 10,
      totalPages: 1,
    });
    await store.loadGlobalRankings();
    resolve(page(2));
    await pending;
    expect(store.globalRankings.map((r) => r.rank)).toEqual([9]);
    expect(store.hasMoreRankings).toBe(false);
    expect(store.loadingMoreRankings).toBe(false);
    expect(store.loadMoreRankingsError).toBeNull();
  });
});
