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
    expect(currentPage.classes()).toContain("text-[var(--terminal-amber)]");
    expect(currentPage.classes()).toContain(
      "dark:bg-[oklch(0.6545_0.1340_85.7_/_0.18)]",
    );
  });
});
