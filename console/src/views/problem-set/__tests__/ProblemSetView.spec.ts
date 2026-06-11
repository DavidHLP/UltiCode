import { shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ProblemSetView from "../ProblemSetView.vue";

vi.mock("vue-router", () => ({
  useRoute: () => ({ params: {} }),
}));

vi.mock("@/components/problem/ProblemExplorer.vue", () => ({
  default: { template: "<div />" },
}));

vi.mock("@/components/problem/ProblemSetSidebar.vue", () => ({
  default: { template: "<div />" },
}));

vi.mock("@/components/problem/FeaturedBanners.vue", () => ({
  default: { template: "<div />" },
}));

vi.mock("vue-i18n", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key }),
}));

describe("ProblemSetView", () => {
  beforeEach(() => {
    document.title = "";
  });

  it("keeps the daily challenge visible before the problem list on narrow screens", () => {
    const wrapper = shallowMount(ProblemSetView);
    const sidebar = wrapper.get('[data-testid="problem-set-sidebar"]');
    const problemList = wrapper.get("main");

    expect(sidebar.classes()).not.toContain("hidden");
    expect(sidebar.classes()).toContain("order-1");
    expect(sidebar.classes()).toContain("max-w-md");
    expect(problemList.classes()).toContain("order-2");
  });
});
