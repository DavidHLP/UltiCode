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

  it("tracks ongoing and past loads independently", async () => {
    type ContestPage = PaginatedResult<ContestListItem>;

    const upcoming = deferred<ContestPage>();
    const running = deferred<ContestPage>();
    const past = deferred<ContestPage>();

    vi.mocked(fetchUpcomingContests).mockReturnValue(upcoming.promise);
    vi.mocked(fetchRunningContests).mockReturnValue(running.promise);
    vi.mocked(fetchPastContests).mockReturnValue(past.promise);

    const store = useContestBrowseStore();
    const ongoingLoad = store.loadContests();
    const pastLoad = store.loadPastContests(2, 10);

    expect(store.loadingContests).toBe(true);
    expect(store.loadingPastContests).toBe(true);

    past.resolve({ items: [], total: 0, page: 2, pageSize: 10, totalPages: 0 });
    await pastLoad;

    expect(store.loadingPastContests).toBe(false);
    expect(store.loadingContests).toBe(true);

    upcoming.resolve({ items: [], total: 0, page: 1, pageSize: 10, totalPages: 0 });
    running.resolve({ items: [], total: 0, page: 1, pageSize: 10, totalPages: 0 });
    await ongoingLoad;

    expect(store.loadingContests).toBe(false);
    expect(store.loadingPastContests).toBe(false);
  });
});
