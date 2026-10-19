import { flushPromises, shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { nextTick, ref } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";
import ContestRankingsView from "../ContestRankingsView.vue";

const { loadGlobalRankings, loadMoreGlobalRankings } = vi.hoisted(() => ({
  loadGlobalRankings: vi.fn(),
  loadMoreGlobalRankings: vi.fn(),
}));
const rankingError = ref<string | null>(null);
vi.mock("@/stores/contestRanking", () => ({
  useContestRankingStore: () => ({
    globalRankings: ref([]),
    loadingRankings: ref(false),
    hasMoreRankings: ref(true),
    loadingMoreRankings: ref(false),
    loadMoreRankingsError: ref(null),
    loadMoreGlobalRankings,
    error: rankingError,
    loadGlobalRankings,
  }),
}));
vi.mock("pinia", () => ({ storeToRefs: (store: unknown) => store }));
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ user: { country: "CN" } }),
}));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));
vi.mock("@/composables/contest/useContestSocket", () => ({
  useContestSocket: () => ({
    isConnected: ref(true),
    joinContest: vi.fn(),
    leaveContest: vi.fn(),
    onRankingUpdate: () => vi.fn(),
  }),
}));
vi.mock("vue-sonner", () => ({ toast: { error: vi.fn() } }));

describe("ContestRankingsView scope navigation", () => {
  beforeEach(() => {
    loadGlobalRankings.mockReset().mockResolvedValue(undefined);
    loadMoreGlobalRankings.mockReset().mockResolvedValue(undefined);
    rankingError.value = null;
  });

  it("loads the linked local scope and keeps buttons and navigation in sync", async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: "/contest/rankings", component: ContestRankingsView }],
    });
    await router.push("/contest/rankings?scope=local&filter=kept");
    const wrapper = shallowMount(ContestRankingsView, {
      global: { plugins: [router] },
    });
    await flushPromises();
    expect(loadGlobalRankings).toHaveBeenLastCalledWith({
      page: 1,
      limit: 10,
      country: "CN",
    });
    expect(
      wrapper.findComponent({ name: "GlobalRanking" }).props("scope"),
    ).toBe("local");

    const globalButton = wrapper.findAllComponents({ name: "Button" })[0];
    expect(globalButton.attributes("aria-pressed")).toBe("false");
    await globalButton.vm.$emit("click");
    await flushPromises();
    expect(router.currentRoute.value.query).toEqual({
      scope: "global",
      filter: "kept",
    });
    expect(loadGlobalRankings).toHaveBeenLastCalledWith({ page: 1, limit: 10 });
    expect(globalButton.attributes("aria-pressed")).toBe("true");

    await router.push("/contest/rankings?scope=local");
    await flushPromises();
    expect(
      wrapper.findComponent({ name: "GlobalRanking" }).props("scope"),
    ).toBe("local");
    expect(loadGlobalRankings).toHaveBeenLastCalledWith({
      page: 1,
      limit: 10,
      country: "CN",
    });
    const pagination = wrapper.findComponent({ name: "GlobalRanking" });
    expect(pagination.props("hasMore")).toBe(true);
    await pagination.vm.$emit("loadMore");
    await flushPromises();
    expect(loadMoreGlobalRankings).toHaveBeenCalledTimes(1);
    rankingError.value = "request failed";
    await nextTick();
    const ranking = wrapper.findComponent({ name: "GlobalRanking" });
    expect(ranking.props("error")).toBe(true);
    loadGlobalRankings.mockClear();
    await ranking.vm.$emit("retry");
    await flushPromises();
    expect(loadGlobalRankings).toHaveBeenCalledWith({
      page: 1,
      limit: 10,
      country: "CN",
    });
    wrapper.unmount();
  });
});
