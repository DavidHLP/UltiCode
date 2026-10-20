import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { ProblemList, UserProblemListsResponse } from "@/types/problem-list";
import { useProblemListsStore } from "@/stores/problemLists";

const mocks = vi.hoisted(() => ({
  fetchCurrentUserId: vi.fn(),
  fetchFeaturedProblemLists: vi.fn(),
  fetchProblemListsOverview: vi.fn(),
}));

vi.mock("@/api/problem-list", () => ({
  fetchFeaturedProblemLists: mocks.fetchFeaturedProblemLists,
  fetchProblemListsOverview: mocks.fetchProblemListsOverview,
}));
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ fetchCurrentUserId: mocks.fetchCurrentUserId }),
}));

const featuredList: ProblemList = {
  id: "featured-1",
  name: "Featured list",
  problemCount: 1,
};

const ownList: ProblemList = {
  id: "own-1",
  name: "Private list",
  problemCount: 1,
};

const savedList: ProblemList = {
  id: "saved-1",
  name: "Saved list",
  problemCount: 1,
};

const userOverview: UserProblemListsResponse = {
  ownLists: [ownList],
  savedLists: [savedList],
  featuredLists: [featuredList],
  categories: [
    { id: "category-1", name: "Private category", sortOrder: 0, lists: [ownList] },
  ],
};

describe("useProblemListsStore", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
    mocks.fetchCurrentUserId.mockReturnValue(null);
    mocks.fetchFeaturedProblemLists.mockResolvedValue([featuredList]);
  });

  afterEach(() => vi.restoreAllMocks());

  it("loads public featured lists for guests without exposing user lists", async () => {
    const store = useProblemListsStore();

    await store.loadOverview();

    expect(mocks.fetchFeaturedProblemLists).toHaveBeenCalledOnce();
    expect(mocks.fetchProblemListsOverview).not.toHaveBeenCalled();
    expect(store.featuredLists).toEqual([featuredList]);
    expect(store.ownLists).toEqual([]);
    expect(store.savedLists).toEqual([]);
    expect(store.categories).toEqual([]);
  });

  it("shows a load error instead of treating a failed request as an empty list", async () => {
    const error = new Error("request failed");
    mocks.fetchFeaturedProblemLists.mockRejectedValue(error);
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    const store = useProblemListsStore();

    await store.loadOverview();

    expect(store.featuredLists).toEqual([]);
    expect(store.loadError).toBe(true);
    expect(consoleError).toHaveBeenCalledWith(
      "Failed to load problem lists overview",
      error,
    );
  });

  it("clears private list data when switching from an authenticated user to guest", async () => {
    mocks.fetchCurrentUserId.mockReturnValue("user-1");
    mocks.fetchProblemListsOverview.mockResolvedValue(userOverview);
    const store = useProblemListsStore();

    await store.loadOverview();
    expect(store.ownLists).toEqual([ownList]);
    expect(store.savedLists).toEqual([savedList]);

    mocks.fetchCurrentUserId.mockReturnValue(null);
    await store.loadOverview();

    expect(store.ownLists).toEqual([]);
    expect(store.savedLists).toEqual([]);
    expect(store.categories).toEqual([]);
    expect(store.featuredLists).toEqual([featuredList]);
    expect(mocks.fetchFeaturedProblemLists).toHaveBeenCalledOnce();
  });

  it("ignores a user response that resolves after switching to guest", async () => {
    mocks.fetchCurrentUserId.mockReturnValue("user-1");
    let resolveOverview!: (value: UserProblemListsResponse) => void;
    const pendingOverview = new Promise<UserProblemListsResponse>((resolve) => {
      resolveOverview = resolve;
    });
    mocks.fetchProblemListsOverview.mockReturnValueOnce(pendingOverview);
    const store = useProblemListsStore();
    const userLoad = store.loadOverview();

    mocks.fetchCurrentUserId.mockReturnValue(null);
    await store.loadOverview();
    resolveOverview(userOverview);
    await userLoad;

    expect(store.ownLists).toEqual([]);
    expect(store.savedLists).toEqual([]);
    expect(store.categories).toEqual([]);
    expect(store.featuredLists).toEqual([featuredList]);
    expect(store.isLoading).toBe(false);
  });
});
