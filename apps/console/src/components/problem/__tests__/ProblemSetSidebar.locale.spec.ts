import { mount, flushPromises, RouterLinkStub } from "@vue/test-utils";
import { createI18n } from "vue-i18n";
import { expect, it, vi } from "vitest";
import ProblemSetSidebar from "../ProblemSetSidebar.vue";
import enProblem from "@/i18n/locales/en-US/problem";
import zhProblem from "@/i18n/locales/zh-CN/problem";

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ isAuthenticated: true }),
}));
vi.mock("@/api/problem", () => ({
  fetchRandomProblem: vi.fn().mockResolvedValue({
    id: 7,
    slug: "merge-lists",
    title: "合并链表",
    difficulty: "HARD",
  }),
}));
vi.mock("@/api/submission", () => ({
  fetchDailyActivity: vi.fn().mockResolvedValue(["2026-10-15"]),
}));
vi.mock("@ulticode/design-system/ui/calendar", () => ({
  Calendar: {
    props: ["locale"],
    template: '<div class="calendar-stub" :data-locale="locale" />',
  },
}));

it("updates the compact challenge, weekdays, day unit and toggle label with locale", async () => {
  const i18n = createI18n({
    legacy: false,
    locale: "en-US",
    messages: {
      "en-US": { problem: enProblem },
      "zh-CN": { problem: zhProblem },
    },
  });
  const wrapper = mount(ProblemSetSidebar, {
    global: { plugins: [i18n], stubs: { RouterLink: RouterLinkStub } },
  });
  try {
    await flushPromises();
    expect(wrapper.get(".calendar-stub").attributes("data-locale")).toBe(
      "en-US",
    );
    expect(wrapper.text()).toContain("Today: 7.");
    expect(wrapper.text()).toContain("(Hard)");
    expect(wrapper.text()).toMatch(/1\s*day(?!s)/);
    expect(wrapper.text()).toContain("Thu");
    expect(i18n.global.t("problem.sidebar.daysUnit", 0)).toBe("days");
    expect(i18n.global.t("problem.sidebar.daysUnit", 2)).toBe("days");
    const toggle = wrapper.get('button[aria-label="Toggle calendar"]');
    expect(toggle.attributes("aria-expanded")).toBe("false");
    await toggle.trigger("click");
    expect(toggle.attributes("aria-expanded")).toBe("true");
    i18n.global.locale.value = "zh-CN";
    await flushPromises();
    expect(wrapper.get(".calendar-stub").attributes("data-locale")).toBe(
      "zh-CN",
    );
    expect(wrapper.text()).toContain("今日：7.");
    expect(wrapper.text()).toContain("(困难)");
    expect(wrapper.text()).toContain("周四");
    expect(wrapper.text()).toMatch(/1\s*天/);
    expect(wrapper.get('button[aria-label="切换日历"]').exists()).toBe(true);
    expect(wrapper.text()).not.toContain("Today:");
  } finally {
    wrapper.unmount();
  }
});
