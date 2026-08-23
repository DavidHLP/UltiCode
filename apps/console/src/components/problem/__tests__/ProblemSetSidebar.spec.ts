import { describe, it, expect, vi, beforeEach } from "vitest";
import { mount, flushPromises, RouterLinkStub } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";
import type * as VueI18n from "vue-i18n";
import ProblemSetSidebar from "../ProblemSetSidebar.vue";
import { fetchRandomProblem } from "@/api/problem";
import type { Problem } from "@/types/problem";

vi.mock("@/api/problem", () => ({
  fetchRandomProblem: vi.fn(),
}));

vi.mock("@/api/submission", () => ({
  fetchDailyActivity: vi.fn(),
}));

vi.mock("vue-i18n", async (importOriginal) => ({
  ...(await importOriginal<typeof VueI18n>()),
  useI18n: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock("@/components/ui/calendar", () => ({
  Calendar: { template: "<div class=\"calendar-stub\" />" },
}));

vi.mock("@/components/ui/card", () => ({
  Card: { template: "<div><slot /></div>" },
  CardContent: { template: "<div><slot /></div>" },
  CardHeader: { template: "<div><slot /></div>" },
  CardTitle: { template: "<div><slot /></div>" },
}));

vi.mock("@/components/ui/badge", () => ({
  Badge: { template: "<span><slot /></span>" },
}));

vi.mock("lucide-vue-next", () => ({
  Trophy: { template: "<svg />" },
  ChevronDown: { template: "<svg />" },
  ChevronUp: { template: "<svg />" },
}));

describe("ProblemSetSidebar", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it("fetches random problem dynamically on mount", async () => {
    const mockProblem: Problem = {
      id: 42,
      title: "两数之和",
      slug: "two-sum",
      difficulty: "EASY",
      acceptance_rate: 50.0,
      tags: [],
    };
    vi.mocked(fetchRandomProblem).mockResolvedValueOnce(mockProblem);

    const wrapper = mount(ProblemSetSidebar, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
        },
      },
    });
    await flushPromises();

    expect(fetchRandomProblem).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain("今日：42.");
    expect(wrapper.text()).toContain("两数之和");
    const link = wrapper.findComponent(RouterLinkStub);
    expect(link.exists()).toBe(true);
    expect(link.props().to).toBe("/problems/two-sum");
  });

  it("handles empty database or fetch error gracefully without failing", async () => {
    vi.mocked(fetchRandomProblem).mockRejectedValueOnce(new Error("No published problems available"));

    const wrapper = mount(ProblemSetSidebar, {
      global: {
        stubs: {
          RouterLink: RouterLinkStub,
        },
      },
    });
    await flushPromises();

    expect(fetchRandomProblem).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).not.toContain("今日：");
  });
});
