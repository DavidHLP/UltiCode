import { defineComponent, ref } from "vue";
import { mount, flushPromises, enableAutoUnmount } from "@vue/test-utils";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ContestDetail } from "@/types/contest";
import { fetchContestRanking, fetchLiveRanking } from "@/api/contest";
import { useContestRankings } from "../useContestRankings";

vi.mock("@/api/contest", () => ({
  fetchContestRanking: vi.fn(),
  fetchLiveRanking: vi.fn(),
}));

enableAutoUnmount(afterEach);
afterEach(() => {
  vi.resetAllMocks();
  vi.useRealTimers();
});

function setup(status = "FINISHED") {
  const contest = ref<ContestDetail | null>({ status } as ContestDetail);
  let state!: ReturnType<typeof useContestRankings>;
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useContestRankings(ref("linked-list-special"), contest);
        return () => null;
      },
    }),
  );
  return { state, wrapper, contest };
}

describe("useContestRankings pagination", () => {
  it("ignores a response after the contest was cleared", async () => {
    let finish!: (
      value: Awaited<ReturnType<typeof fetchContestRanking>>,
    ) => void;
    vi.mocked(fetchContestRanking).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    const { state, wrapper, contest } = setup();
    contest.value = null;
    await flushPromises();
    finish({ items: [], page: 1, pageSize: 20, total: 180, totalPages: 9 });
    await flushPromises();
    expect(state.totalPages.value).toBe(1);
    expect(state.loading.value).toBe(false);
    wrapper.unmount();
  });

  it("requests every page instead of hiding rows after the preview", async () => {
    vi.mocked(fetchContestRanking).mockResolvedValue({
      items: [],
      page: 1,
      pageSize: 20,
      total: 21,
      totalPages: 2,
    });
    const { state, wrapper } = setup();
    await flushPromises();
    expect(fetchContestRanking).toHaveBeenCalledWith("linked-list-special", {
      page: 1,
      limit: 20,
    });
    expect(state.totalPages.value).toBe(2);
    await state.loadRankings(0);
    await state.loadRankings(3);
    expect(fetchContestRanking).toHaveBeenCalledTimes(1);
    await state.loadRankings(2);
    expect(fetchContestRanking).toHaveBeenLastCalledWith(
      "linked-list-special",
      { page: 2, limit: 20 },
    );
    expect(state.page.value).toBe(2);
    wrapper.unmount();
  });

  it("exposes a failed page for retry and normalizes an empty total", async () => {
    vi.mocked(fetchContestRanking).mockRejectedValueOnce(new Error("offline"));
    const { state, wrapper } = setup();
    await flushPromises();
    expect(state.error.value).toBe(true);
    expect(state.loading.value).toBe(false);
    vi.mocked(fetchContestRanking).mockResolvedValueOnce({
      items: [],
      page: 1,
      pageSize: 20,
      total: 0,
      totalPages: 0,
    });
    await state.loadRankings();
    expect(state.error.value).toBe(false);
    expect(state.totalPages.value).toBe(1);
    wrapper.unmount();
  });

  it("retains live polling and cleans it up on unmount", async () => {
    vi.useFakeTimers();
    vi.mocked(fetchLiveRanking).mockResolvedValue([]);
    const { state, wrapper } = setup("RUNNING");
    await flushPromises();
    expect(fetchLiveRanking).toHaveBeenCalledTimes(1);
    expect(state.loading.value).toBe(false);
    await vi.advanceTimersByTimeAsync(30000);
    expect(fetchLiveRanking).toHaveBeenCalledTimes(2);
    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(30000);
    expect(fetchLiveRanking).toHaveBeenCalledTimes(2);
    vi.useRealTimers();
  });
});
