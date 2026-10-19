import { mount, flushPromises } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ContestBrowseView from "../ContestBrowseView.vue";
import { useContestBrowseStore } from "@/stores/contestBrowse";
import {
  fetchPastContests,
  fetchRunningContests,
  fetchUpcomingContests,
} from "@/api/contest";
import type { ContestListItem, PaginatedResult } from "@/types/contest";

const route = vi.hoisted(() => ({ query: {} as Record<string, unknown> }));
vi.mock("vue-router", () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: vi.fn().mockResolvedValue(undefined) }),
}));
vi.mock("vue-i18n", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key }),
}));
vi.mock("@/api/contest", () => ({
  fetchPastContests: vi.fn(),
  fetchRunningContests: vi.fn(),
  fetchUpcomingContests: vi.fn(),
}));
const emptyPage: PaginatedResult<ContestListItem> = {
  items: [],
  total: 0,
  page: 1,
  pageSize: 10,
  totalPages: 0,
};
const cachedContest = {
  id: "cached",
  slug: "cached",
  title: "Cached contest",
} as ContestListItem;
function render(tab = "ongoing") {
  return mount(ContestBrowseView, {
    props: { initialTab: tab },
    global: {
      stubs: {
        ContestCard: {
          props: ["contest"],
          template: '<div data-testid="contest-card">{{ contest.title }}</div>',
        },
      },
    },
  });
}
beforeEach(() => {
  setActivePinia(createPinia());
  route.query = {};
  vi.mocked(fetchRunningContests).mockReset().mockResolvedValue(emptyPage);
  vi.mocked(fetchUpcomingContests).mockReset().mockResolvedValue(emptyPage);
  vi.mocked(fetchPastContests).mockReset().mockResolvedValue(emptyPage);
});

describe("ContestBrowseView read failures", () => {
  it.each(["ongoing", "upcoming"])(
    "reports and retries %s without success empty state or remounting tabs",
    async (tab) => {
      vi.mocked(fetchRunningContests).mockRejectedValueOnce(
        new Error("offline"),
      );
      const wrapper = render(tab);
      try {
        await flushPromises();
        expect(wrapper.get('[role="alert"]').text()).toContain(
          "contest.list.loadError",
        );
        expect(wrapper.text()).not.toContain("contest.list.noContests");
        const control = wrapper.findAll('[role="tab"]')[0]!.element;
        let finish!: (value: PaginatedResult<ContestListItem>) => void;
        vi.mocked(fetchRunningContests).mockReturnValueOnce(
          new Promise((resolve) => {
            finish = resolve;
          }),
        );
        await wrapper.get('[role="alert"] button').trigger("click");
        await flushPromises();
        expect(wrapper.findAll('[role="tab"]')[0]!.element).toBe(control);
        finish(emptyPage);
        await flushPromises();
        expect(wrapper.find('[role="alert"]').exists()).toBe(false);
        expect(
          wrapper.get('[role="tabpanel"][data-state="active"]').text(),
        ).toContain("contest.list.noContests");
        expect(fetchRunningContests).toHaveBeenCalledTimes(2);
        expect(fetchPastContests).toHaveBeenCalledTimes(1);
      } finally {
        wrapper.unmount();
      }
    },
  );

  it.each([false, true])(
    "hides past success content and pagination on failure (cached=%s)",
    async (cached) => {
      const store = useContestBrowseStore();
      if (cached) {
        store.pastContests = [cachedContest];
        store.pastContestsTotal = 21;
      }
      vi.mocked(fetchPastContests).mockRejectedValueOnce(new Error("offline"));
      const wrapper = render("finished");
      try {
        await flushPromises();
        expect(wrapper.get('[role="alert"]').text()).toContain(
          "contest.list.loadError",
        );
        expect(wrapper.text()).not.toContain("contest.list.noContests");
        expect(wrapper.find('[data-testid="contest-card"]').exists()).toBe(
          false,
        );
        expect(wrapper.text()).not.toContain("Next page");
        await wrapper.get('[role="alert"] button').trigger("click");
        await flushPromises();
        expect(wrapper.find('[role="alert"]').exists()).toBe(false);
        expect(wrapper.text()).toContain("contest.list.noFinishedHint");
        expect(fetchPastContests).toHaveBeenCalledTimes(2);
        expect(fetchRunningContests).toHaveBeenCalledTimes(1);
      } finally {
        wrapper.unmount();
      }
    },
  );

  it("keeps successful past results available when the other list fails", async () => {
    vi.mocked(fetchRunningContests).mockRejectedValueOnce(new Error("offline"));
    vi.mocked(fetchPastContests).mockResolvedValueOnce({
      ...emptyPage,
      items: [cachedContest],
      total: 1,
    });
    const wrapper = render("finished");
    try {
      await flushPromises();
      expect(wrapper.find('[role="alert"]').exists()).toBe(false);
      expect(wrapper.get('[data-testid="contest-card"]').text()).toBe(
        "Cached contest",
      );
    } finally {
      wrapper.unmount();
    }
  });

  it("waits for both initial reads before exposing retry controls", async () => {
    let finish!: (value: PaginatedResult<ContestListItem>) => void;
    vi.mocked(fetchPastContests).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      }),
    );
    vi.mocked(fetchRunningContests).mockRejectedValueOnce(new Error("offline"));
    const wrapper = render();
    try {
      await flushPromises();
      expect(wrapper.find('[role="tab"]').exists()).toBe(false);
      finish(emptyPage);
      await flushPromises();
      expect(wrapper.findAll('[role="tab"]')).toHaveLength(3);
      expect(wrapper.find('[role="alert"]').exists()).toBe(true);
    } finally {
      finish(emptyPage);
      wrapper.unmount();
    }
  });
});
