import { mount, flushPromises } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MyContests from "../MyContests.vue";
import ContestMyView from "../../ContestMyView.vue";
import RetryButton from "@/components/common/loading/RetryButton.vue";

const store = vi.hoisted(() => ({
  registeredContests: [],
  contestHistory: [],
  virtualContests: [],
  loadUserContests: vi.fn(),
  loadContestHistory: vi.fn(),
}));
vi.mock("@/stores/contestRanking", () => ({
  useContestRankingStore: () => store,
}));
vi.mock("vue-router", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key, locale: { value: "en-US" } }),
}));

beforeEach(() => {
  store.loadUserContests.mockReset().mockResolvedValue(undefined);
  store.loadContestHistory.mockReset().mockResolvedValue(undefined);
});

describe("MyContests", () => {
  it("loads the initial category once through its owning component", async () => {
    const wrapper = mount(ContestMyView);
    try {
      await flushPromises();
      expect(store.loadUserContests).toHaveBeenCalledExactlyOnceWith(
        "registered",
      );
    } finally {
      wrapper.unmount();
    }
  });

  it.each([
    [0, "registered", "contest.myContests.noRegistered"],
    [1, "participated", "contest.myContests.noParticipated"],
    [2, "virtual", "contest.myContests.noVirtual"],
  ] as const)(
    "reports and retries the failed %s category without a success empty state",
    async (index, category, emptyMessage) => {
      if (index === 0)
        store.loadUserContests.mockRejectedValueOnce(new Error("offline"));
      if (index === 1)
        store.loadContestHistory.mockRejectedValueOnce(new Error("offline"));
      if (index === 2)
        store.loadUserContests
          .mockResolvedValueOnce(undefined)
          .mockRejectedValueOnce(new Error("offline"));
      const wrapper = mount(MyContests);
      try {
        await flushPromises();
        if (index > 0) {
          await wrapper
            .findAll("[role=tab]")
            [index]!.trigger("keydown", { key: "Enter" });
          await flushPromises();
        }
        expect(wrapper.get('[role="alert"]').text()).toContain(
          "contest.myContests.loadError",
        );
        expect(
          wrapper
            .findAll('[role="tabpanel"]')
            .every((panel) => !panel.isVisible()),
        ).toBe(true);
        expect(wrapper.findAll('[role="tab"]')).toHaveLength(3);
        await wrapper.getComponent(RetryButton).trigger("click");
        await flushPromises();
        expect(wrapper.find('[role="alert"]').exists()).toBe(false);
        expect(
          wrapper.get('[role="tabpanel"][data-state="active"]').text(),
        ).toContain(emptyMessage);
        if (category === "participated")
          expect(store.loadContestHistory).toHaveBeenCalledTimes(2);
        else expect(store.loadUserContests).toHaveBeenLastCalledWith(category);
      } finally {
        wrapper.unmount();
      }
    },
  );
  it("keeps the tab controls mounted while a category loads", async () => {
    let finish!: () => void;
    store.loadUserContests.mockReturnValueOnce(
      new Promise<void>((resolve) => {
        finish = resolve;
      }),
    );
    const wrapper = mount(MyContests);
    await flushPromises();
    expect(wrapper.find("h2").exists()).toBe(false);
    expect(wrapper.findAll("[role=tab]")).toHaveLength(3);
    const registered = wrapper.findAll("[role=tab]")[0]!.element;
    expect(wrapper.text()).toContain("contest.myContests.loading");
    finish();
    await flushPromises();
    expect(wrapper.findAll("[role=tab]")[0]!.element).toBe(registered);
    expect(wrapper.text()).toContain("contest.myContests.noRegistered");
    wrapper.unmount();
  });
});
