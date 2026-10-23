import { mount, RouterLinkStub } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import PastContests from "../PastContests.vue";
import en from "@/i18n/locales/en-US/contest";
import zh from "@/i18n/locales/zh-CN/contest";
import {
  ContestStatus,
  ContestType,
  type ContestListItem,
} from "@/types/contest";

vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("@/i18n/utils/locale", () => ({
  getActiveLocale: () => "en-US",
}));

describe("PastContests", () => {
  it("renders the past-contest heading without an unrelated partner marker", () => {
    const wrapper = mount(PastContests, {
      props: {
        contests: [],
        loading: false,
        currentPage: 1,
        totalPages: 1,
      },
    });
    expect(wrapper.get("h2").text()).toBe("contest.list.past");
    expect(wrapper.text()).toContain("contest.list.pastSubtitle");
    expect(wrapper.text()).not.toContain("contest.list.partner");
    expect(wrapper.find('[data-slot="badge"]').exists()).toBe(false);
  });

  it("retains the complete date and provides keyboard-accessible contest links", () => {
    const contest: ContestListItem = {
      id: "past-1",
      slug: "past-1",
      title: "Past contest",
      status: ContestStatus.FINISHED,
      startTime: "2026-09-03T12:13:00Z",
      endTime: "2026-09-03T13:43:00Z",
      duration: 90,
      contestType: ContestType.ICPC,
      participantCount: 0,
      registeredCount: 2,
      problemCount: 3,
      maxParticipants: 0,
      isPremium: false,
      isPublished: true,
      isVisible: true,
      isParticipating: false,
      userRanking: 0,
      isRated: false,
      scoringMode: "ICPC",
      penaltyPerWrong: 20,
      coverImage: "",
    };
    const wrapper = mount(PastContests, {
      props: {
        contests: [contest],
        loading: false,
        currentPage: 1,
        totalPages: 1,
      },
      global: { stubs: { RouterLink: RouterLinkStub } },
    });
    expect(wrapper.text()).toContain("Sep 3, 2026");
    const card = wrapper.getComponent(RouterLinkStub);
    expect(card.props("to")).toEqual({
      name: "contest-detail",
      params: { slug: "past-1" },
    });
    expect(card.attributes("aria-label")).toBe("Past contest");
    expect(card.get('[data-slot="button"]').element.tagName).toBe("SPAN");
  });

  it("localizes the table headers and hides empty or single-page pagination", async () => {
    const wrapper = mount(PastContests, {
      props: {
        contests: [],
        loading: false,
        currentPage: 1,
        totalPages: 0,
      },
    });
    expect(wrapper.text()).toContain("contest.list.tableHeaders.contest");
    expect(wrapper.find('[aria-label="common.pagination.previousPage"]').exists()).toBe(false);
    expect(wrapper.find('[aria-label="common.pagination.nextPage"]').exists()).toBe(false);
    await wrapper.setProps({ totalPages: 1 });
    expect(wrapper.find("[aria-current=page]").exists()).toBe(false);
    await wrapper.setProps({ totalPages: 3 });
    const previous = () => wrapper.get('[aria-label="common.pagination.previousPage"]');
    const next = () => wrapper.get('[aria-label="common.pagination.nextPage"]');
    expect(previous().attributes("disabled")).toBeDefined();
    expect(next().attributes("disabled")).toBeUndefined();
    await next().trigger("click");
    expect(wrapper.emitted("update:currentPage")).toEqual([[2]]);
    await wrapper.setProps({ currentPage: 3 });
    expect(previous().attributes("disabled")).toBeUndefined();
    expect(next().attributes("disabled")).toBeDefined();
    expect(wrapper.get("[aria-current=page]").text()).toBe("3");
    expect(en.list.tableHeaders).toEqual({
      contest: "Contest",
      date: "Date",
      duration: "Duration",
      status: "Status",
      action: "Action",
    });
    expect(zh.list.tableHeaders).toEqual({
      contest: "比赛",
      date: "日期",
      duration: "时长",
      status: "状态",
      action: "操作",
    });
  });

  it("uses theme-aware terminal colors for the current page", () => {
    const wrapper = mount(PastContests, {
      props: {
        contests: [],
        loading: false,
        currentPage: 1,
        totalPages: 3,
      },
    });

    const currentPage = wrapper.get(
      '[data-testid="past-contests-current-page"]',
    );

    expect(currentPage.text()).toBe("1");
    expect(currentPage.attributes("aria-current")).toBe("page");
    expect(currentPage.classes()).toContain("text-foreground");
    expect(currentPage.classes()).toContain("bg-status-warning-surface");
  });

  it("renders pager errors and emits retry", async () => {
    const wrapper = mount(PastContests, {
      props: {
        contests: [],
        loading: false,
        currentPage: 1,
        totalPages: 0,
        error: "Failed to load past contests",
      },
    });

    expect(wrapper.get('[role="alert"]').text()).toContain(
      "Failed to load past contests",
    );

    await wrapper.get('[role="alert"] button').trigger("click");
    expect(wrapper.emitted("retry")).toHaveLength(1);
  });
});
