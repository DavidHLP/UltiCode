import { beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import {
  fetchPastContests,
  fetchRunningContests,
  fetchUpcomingContests,
} from "@/api/contest";
import type { ContestListItem, PaginatedResult } from "@/types/contest";
import { useContestBrowseStore } from "@/stores/contestBrowse";

vi.mock("@/api/contest", () => ({
  fetchPastContests: vi.fn(),
  fetchRunningContests: vi.fn(),
  fetchUpcomingContests: vi.fn(),
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve;
  });
  return { promise, resolve };
}

describe("useContestBrowseStore loading state", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it("keeps ongoing loading separate while the pager owns past loading", async () => {
    type ContestPage = PaginatedResult<ContestListItem>;

    const upcoming = deferred<ContestPage>();
    const running = deferred<ContestPage>();
    const past = deferred<ContestPage>();
    const pastItem = {
      id: "past-1",
      slug: "past-1",
      title: "Past contest",
    } as ContestListItem;

    vi.mocked(fetchUpcomingContests).mockReturnValue(upcoming.promise);
    vi.mocked(fetchRunningContests).mockReturnValue(running.promise);
    vi.mocked(fetchPastContests).mockReturnValue(past.promise);

    const store = useContestBrowseStore();
    const ongoingLoad = store.loadContests();
    const pastLoad = store.loadPastContests(2, 10);

    expect(store.loadingContests).toBe(true);
    expect("loadingPastContests" in store).toBe(false);

    past.resolve({
      items: [pastItem],
      total: 11,
      page: 2,
      pageSize: 10,
      totalPages: 2,
    });
    await pastLoad;

    expect(store.pastContests).toEqual([pastItem]);
    expect(store.pastContestsTotal).toBe(11);
    expect(store.loadingContests).toBe(true);

    upcoming.resolve({
      items: [],
      total: 0,
      page: 1,
      pageSize: 10,
      totalPages: 0,
    });
    running.resolve({
      items: [],
      total: 0,
      page: 1,
      pageSize: 10,
      totalPages: 0,
    });
    await ongoingLoad;

    expect(store.loadingContests).toBe(false);
  });
  it("exposes past-load failures from request-state", async () => {
    vi.mocked(fetchPastContests).mockRejectedValue(new Error("past failed"));
    const store = useContestBrowseStore();
    await expect(store.loadPastContests()).rejects.toThrow("past failed");
    expect(store.error).toBe("past failed");
  });
});
