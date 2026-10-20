import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { defineComponent, h, type PropType } from "vue";
import type { SolutionFeedItem, SolutionFeedResponse } from "@/types/solution";
import ProblemSolutionsView from "../ProblemSolutionsView.vue";

const { emitHook, fetchSolution, fetchSolutionFeed, handleError } = vi.hoisted(
  () => ({
    emitHook: vi.fn(),
    fetchSolution: vi.fn(),
    fetchSolutionFeed: vi.fn(),
    handleError: vi.fn(),
  }),
);

vi.mock("@/api/solution", () => ({ fetchSolution, fetchSolutionFeed }));
vi.mock("@/hooks/problem-hooks", () => ({
  problemHooks: { emit: emitHook },
}));
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ fetchCurrentUserId: () => null }),
}));
vi.mock("@/composables/useErrorHandler", () => ({
  useErrorHandler: () => ({ handleError }),
}));
vi.mock("vue-i18n", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-i18n")>();
  return {
    ...actual,
    useI18n: () => ({ t: (key: string) => key }),
  };
});

const SolutionListStub = defineComponent({
  name: "SolutionListView",
  props: {
    items: { type: Array as PropType<SolutionFeedItem[]>, required: true },
  },
  emits: ["select"],
  setup(props, { emit }) {
    return () =>
      h("section", { "data-testid": "solution-list" }, [
        ...props.items.map((item) => h("p", { key: item.id }, item.title)),
        h(
          "button",
          {
            "data-testid": "select-first",
            onClick: () => emit("select", props.items[0]),
          },
          "Select first",
        ),
      ]);
  },
});

const SolutionDetailStub = defineComponent({
  name: "SolutionDetail",
  props: { item: { type: Object as PropType<SolutionFeedItem>, required: true } },
  setup(props) {
    return () => h("article", { "data-testid": "solution-detail" }, props.item.title);
  },
});

const makeItem = (problemId: number, id: string, title: string): SolutionFeedItem => ({
  id,
  problem_id: String(problemId),
  title,
  summary: "Summary",
  authorId: "author-1",
  author: { id: "author-1", username: "author", name: "Author", role: "" },
  stats: { views: 0, comments: 0, likes: 0, dislikes: 0 },
  score: 0,
  created_at: "2026-10-19T00:00:00Z",
  publishedAt: "2026-10-19T00:00:00Z",
  language: "python",
  tags: [],
  votes: 0,
  views: 0,
  likes: 0,
});

const makeFeed = (items: SolutionFeedItem[]): SolutionFeedResponse =>
  ({ items, total: items.length, sortOptions: [] }) as SolutionFeedResponse;

const mountView = (problemId: number) =>
  mount(ProblemSolutionsView, {
    props: { problemId, followUp: "Follow-up hint" },
    global: {
      stubs: {
        Button: true,
        SolutionDetail: SolutionDetailStub,
        SolutionListView: SolutionListStub,
      },
    },
  });

const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
};

describe("ProblemSolutionsView", () => {
  beforeEach(() => {
    vi.resetAllMocks();
    emitHook.mockResolvedValue(undefined);
  });

  it("clears selected solution when problem ID changes", async () => {
    const oldItem = makeItem(1, "solution-1", "Two Sum solution");
    const newItem = makeItem(2, "solution-2", "Add Two Numbers solution");
    fetchSolutionFeed
      .mockResolvedValueOnce(makeFeed([oldItem]))
      .mockResolvedValueOnce(makeFeed([newItem]));
    fetchSolution.mockResolvedValue(oldItem);
    const wrapper = mountView(1);
    await flushPromises();

    await wrapper.get('[data-testid="select-first"]').trigger("click");
    await flushPromises();
    expect(wrapper.get('[data-testid="solution-detail"]').text()).toBe(
      "Two Sum solution",
    );

    await wrapper.setProps({ problemId: 2 });
    expect(wrapper.find('[data-testid="solution-detail"]').exists()).toBe(false);
    await flushPromises();
    expect(wrapper.text()).toContain("Add Two Numbers solution");
    expect(wrapper.text()).not.toContain("Two Sum solution");
    wrapper.unmount();
  });

  it("ignores an older feed response after a problem change", async () => {
    const oldFeed = deferred<SolutionFeedResponse>();
    const oldItem = makeItem(1, "solution-1", "Two Sum solution");
    const newItem = makeItem(2, "solution-2", "Add Two Numbers solution");
    fetchSolutionFeed
      .mockReturnValueOnce(oldFeed.promise)
      .mockResolvedValueOnce(makeFeed([newItem]));
    const wrapper = mountView(1);
    await flushPromises();

    await wrapper.setProps({ problemId: 2 });
    await flushPromises();
    expect(wrapper.text()).toContain("Add Two Numbers solution");

    oldFeed.resolve(makeFeed([oldItem]));
    await flushPromises();
    expect(wrapper.text()).toContain("Add Two Numbers solution");
    expect(wrapper.text()).not.toContain("Two Sum solution");
    wrapper.unmount();
  });

  it("ignores a pending detail response after a problem change", async () => {
    const oldItem = makeItem(1, "solution-1", "Two Sum solution");
    const newItem = makeItem(2, "solution-2", "Add Two Numbers solution");
    const oldDetail = deferred<SolutionFeedItem>();
    fetchSolutionFeed
      .mockResolvedValueOnce(makeFeed([oldItem]))
      .mockResolvedValueOnce(makeFeed([newItem]));
    fetchSolution.mockReturnValueOnce(oldDetail.promise);
    const wrapper = mountView(1);
    await flushPromises();

    await wrapper.get('[data-testid="select-first"]').trigger("click");
    await wrapper.setProps({ problemId: 2 });
    await flushPromises();
    oldDetail.resolve(oldItem);
    await flushPromises();

    expect(wrapper.text()).toContain("Add Two Numbers solution");
    expect(wrapper.find('[data-testid="solution-detail"]').exists()).toBe(false);
    wrapper.unmount();
  });
});
