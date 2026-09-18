import { mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";
import PastContests from "../PastContests.vue";

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock("@/i18n/utils/locale", () => ({
  getActiveLocale: () => "en-US",
}));

describe("PastContests", () => {
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
    expect(currentPage.classes()).toContain("text-foreground-strong");
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
