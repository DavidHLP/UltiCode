import { describe, it, expect, vi, beforeEach } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";

const fetchProblemsMock = vi.fn();
const routerPushMock = vi.fn();

vi.mock("@/api/problem", () => ({
  fetchProblems: (...args: unknown[]) => fetchProblemsMock(...args),
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({
    push: (...args: unknown[]) => routerPushMock(...args),
  }),
}));

vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock("@/lib/utils", () => ({
  cn: (...inputs: unknown[]) => inputs.filter(Boolean).join(" "),
}));

// Stub the lucide-vue-next icons that the drawer template imports.
vi.mock("lucide-vue-next", () => ({
  Search: { name: "Search", template: "<i />" },
  ArrowUpDown: { name: "ArrowUpDown", template: "<i />" },
  Filter: { name: "Filter", template: "<i />" },
  ChevronRight: { name: "ChevronRight", template: "<i />" },
  Check: { name: "Check", template: "<i />" },
}));

// Stub the shadcn UI primitives so the test does not need to render Tailwind.
vi.mock("@/components/ui/input", () => ({
  Input: { name: "Input", template: "<input />" },
}));
vi.mock("@/components/ui/button", () => ({
  Button: { name: "Button", template: "<button><slot /></button>" },
}));
vi.mock("@/components/ui/scroll-area", () => ({
  ScrollArea: { name: "ScrollArea", template: "<div><slot /></div>" },
}));

import ProblemListDrawer from "../ProblemListDrawer.vue";

const baseItems = [
  {
    id: 1,
    title: "两数之和",
    slug: "two-sum",
    difficulty: "EASY" as const,
    acceptance_rate: 53.5,
    tags: [],
    status: "todo" as const,
  },
  {
    id: 2,
    title: "两数相加",
    slug: "add-two-numbers",
    difficulty: "MEDIUM" as const,
    acceptance_rate: 41.2,
    tags: [],
    status: "solved" as const,
  },
];

beforeEach(() => {
  vi.clearAllMocks();
});

describe("ProblemListDrawer data fetching", () => {
  it("requests fetchProblems with a pageSize within the backend limit", async () => {
    fetchProblemsMock.mockResolvedValueOnce({
      items: baseItems,
      total: 2,
      page: 1,
      pageSize: 100,
      totalPages: 1,
    });

    mount(ProblemListDrawer, {
      props: { currentProblemId: 1 },
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    });

    await flushPromises();

    expect(fetchProblemsMock).toHaveBeenCalledTimes(1);
    const [, page, pageSize] = fetchProblemsMock.mock.calls[0];
    expect(page).toBe(1);
    expect(pageSize).toBeLessThanOrEqual(100);
  });

  it("populates the problem list when the API returns data", async () => {
    fetchProblemsMock.mockResolvedValueOnce({
      items: baseItems,
      total: 2,
      page: 1,
      pageSize: 100,
      totalPages: 1,
    });

    const wrapper = mount(ProblemListDrawer, {
      props: { currentProblemId: 1 },
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    });

    await flushPromises();

    const html = wrapper.html();
    expect(html).toContain("两数之和");
    expect(html).toContain("两数相加");
  });

  it("renders the empty state when the API call fails", async () => {
    fetchProblemsMock.mockRejectedValueOnce(new Error("network error"));

    const wrapper = mount(ProblemListDrawer, {
      props: { currentProblemId: 1 },
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    });

    await flushPromises();

    expect(wrapper.text()).toContain("problem.drawer.noProblemsFound");
  });

  it("applies the active selection class to the current problem only", async () => {
    fetchProblemsMock.mockResolvedValueOnce({
      items: baseItems,
      total: 2,
      page: 1,
      pageSize: 100,
      totalPages: 1,
    });

    const wrapper = mount(ProblemListDrawer, {
      props: { currentProblemId: 1 },
      global: {
        stubs: {
          RouterLink: { template: "<a><slot /></a>" },
        },
      },
    });

    await flushPromises();

    // The drawer also uses `group` on the search input wrapper, so match
    // the problem rows by their unique flex + padding combination.
    const rows = wrapper.findAll("div.group.flex.items-center.justify-between");
    expect(rows).toHaveLength(2);

    const activeRow = rows.find((row) =>
      row.classes().includes("bg-primary/10"),
    );
    const inactiveRow = rows.find(
      (row) => !row.classes().includes("bg-primary/10"),
    );

    expect(
      activeRow,
      "active row should use the primary accent background",
    ).toBeDefined();
    expect(
      inactiveRow,
      "inactive row should not use the primary accent background",
    ).toBeDefined();

    // The active row must use the project's solarized tokens, not raw zinc
    // colors that ignore the dark/light theme.
    expect(activeRow!.classes()).toContain("border-primary");
    expect(activeRow!.classes()).toContain("text-primary");
    expect(activeRow!.classes().some((c) => c.startsWith("bg-zinc"))).toBe(
      false,
    );
    expect(activeRow!.classes().some((c) => c.startsWith("text-white"))).toBe(
      false,
    );

    // The inactive row keeps the left-border slot reserved with a
    // transparent border so toggling the active state does not shift the
    // content.
    expect(inactiveRow!.classes()).toContain("border-transparent");
    expect(inactiveRow!.classes()).toContain("hover:bg-muted/50");
    expect(inactiveRow!.classes()).toContain("text-foreground");
  });
});
