import { nextTick } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  PAST_CONTESTS_PAGE_SIZE,
  usePastContestsPager,
} from "../usePastContestsPager";

const mocks = vi.hoisted(() => ({
  route: { query: {} as Record<string, unknown> },
  replace: vi.fn().mockResolvedValue(undefined),
  store: {
    pastContestsTotal: 0,
    loadPastContests: vi.fn().mockResolvedValue(undefined),
  },
}));

vi.mock("vue-router", () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace }),
}));

vi.mock("@/stores/contestBrowse", () => ({
  useContestBrowseStore: () => mocks.store,
}));

describe("usePastContestsPager", () => {
  beforeEach(() => {
    mocks.route.query = {};
    mocks.replace.mockReset().mockResolvedValue(undefined);
    mocks.store.pastContestsTotal = 0;
    mocks.store.loadPastContests.mockReset().mockResolvedValue(undefined);
  });

  it("parses the route page, keeps page size at ten, and calculates total pages", async () => {
    mocks.route.query = { page: "3", tab: "finished" };
    mocks.store.pastContestsTotal = 25;

    const pager = usePastContestsPager();

    expect(pager.page.value).toBe(3);
    expect(pager.pageSize).toBe(PAST_CONTESTS_PAGE_SIZE);
    expect(pager.totalPages.value).toBe(3);

    await pager.loadInitialPage();

    expect(mocks.store.loadPastContests).toHaveBeenCalledWith(
      3,
      PAST_CONTESTS_PAGE_SIZE,
    );
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("loads one transition and serializes the updated page in the route", async () => {
    const pager = usePastContestsPager();
    await pager.loadInitialPage();
    mocks.store.loadPastContests.mockClear();

    pager.page.value = 2;
    await nextTick();
    await Promise.resolve();
    await Promise.resolve();

    expect(mocks.store.loadPastContests).toHaveBeenCalledTimes(1);
    expect(mocks.store.loadPastContests).toHaveBeenCalledWith(
      2,
      PAST_CONTESTS_PAGE_SIZE,
    );
    expect(mocks.replace).toHaveBeenCalledWith({ query: { page: 2 } });
  });

  it("does not duplicate a direct page load when it updates the reactive page", async () => {
    const pager = usePastContestsPager();
    await pager.loadInitialPage();
    mocks.store.loadPastContests.mockClear();
    mocks.replace.mockClear();

    await pager.loadPage(2);
    await nextTick();

    expect(mocks.store.loadPastContests).toHaveBeenCalledTimes(1);
    expect(mocks.replace).toHaveBeenCalledTimes(1);
  });

  it("holds its loading state for the complete load transition", async () => {
    let resolveLoad!: () => void;
    mocks.store.loadPastContests.mockImplementation(
      () => new Promise<void>((resolve) => {
        resolveLoad = resolve;
      }),
    );

    const pager = usePastContestsPager();
    const loadPromise = pager.loadInitialPage();

    expect(pager.loading.value).toBe(true);
    resolveLoad();
    await loadPromise;
    expect(pager.loading.value).toBe(false);
  });
  it("clears its loading state when a transition fails", async () => {
    mocks.store.loadPastContests.mockRejectedValue(new Error("network"));
    const pager = usePastContestsPager();

    await expect(pager.loadInitialPage()).rejects.toThrow("network");
    expect(pager.loading.value).toBe(false);
  });
});
