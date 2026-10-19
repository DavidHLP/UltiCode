import { flushPromises, shallowMount } from "@vue/test-utils";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import ProblemListsView from "../ProblemListsView.vue";
import RetryButton from "@/components/common/loading/RetryButton.vue";
import { fetchProblemListsOverview } from "@/api/problem-list";

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ fetchCurrentUserId: () => "test-user" }),
}));
vi.mock("@/api/problem-list", () => ({
  fetchProblemListsOverview: vi.fn(),
  createProblemList: vi.fn(),
  deleteProblemList: vi.fn(),
  unsaveList: vi.fn(),
  saveList: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
  moveListToCategory: vi.fn(),
}));
vi.mock("vue-router", () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));
beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, "error").mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

it("keeps failed overview counts and empty tabs hidden until retry succeeds", async () => {
  vi.mocked(fetchProblemListsOverview)
    .mockRejectedValueOnce(new Error("Network Error"))
    .mockResolvedValueOnce({
      ownLists: [],
      savedLists: [],
      categories: [],
      featuredLists: [],
    });
  const wrapper = shallowMount(ProblemListsView, {
    global: { renderStubDefaultSlot: true },
  });
  try {
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("personal.messages.loadFailed");
    expect(wrapper.findComponent({ name: "Tabs" }).exists()).toBe(false);
    expect(wrapper.findComponent({ name: "MyListsTab" }).exists()).toBe(false);
    wrapper.getComponent(RetryButton).vm.$emit("retry");
    await flushPromises();
    expect(fetchProblemListsOverview).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.findComponent({ name: "Tabs" }).exists()).toBe(true);
    expect(
      wrapper.findComponent({ name: "MyListsTab" }).props("lists"),
    ).toEqual([]);
  } finally {
    wrapper.unmount();
  }
});
