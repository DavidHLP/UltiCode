import { flushPromises, shallowMount } from "@vue/test-utils";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import SolutionsView from "../SolutionsView.vue";
import RetryButton from "@/components/common/loading/RetryButton.vue";
import { fetchUserSolutions } from "@/api/solution";

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ fetchCurrentUserId: () => "test-user" }),
}));
vi.mock("@/api/solution", () => ({
  fetchUserSolutions: vi.fn(),
  deleteSolution: vi.fn(),
}));
vi.mock("vue-router", async (original) => ({
  ...(await original<typeof import("vue-router")>()),
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("vue-i18n", async (original) => ({
  ...(await original<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key, locale: "en-US" }),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, "error").mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

it("keeps read failure separate from valid empty solutions and retries", async () => {
  vi.mocked(fetchUserSolutions)
    .mockRejectedValueOnce(new Error("Network Error"))
    .mockResolvedValueOnce({
      items: [],
      total: 0,
      page: 1,
      limit: 10,
      totalPages: 0,
    });
  const wrapper = shallowMount(SolutionsView, {
    global: { renderStubDefaultSlot: true },
  });
  try {
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("personal.solutions.loadError");
    expect(wrapper.text()).not.toContain("personal.solutions.noSolutions");
    expect(wrapper.text()).not.toContain("personal.solutions.startCoding");
    wrapper.getComponent(RetryButton).vm.$emit("retry");
    await flushPromises();
    expect(fetchUserSolutions).toHaveBeenCalledTimes(2);
    expect(fetchUserSolutions).toHaveBeenLastCalledWith("test-user");
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain("personal.solutions.noSolutions");
    expect(wrapper.text()).toContain("personal.solutions.startCoding");
  } finally {
    wrapper.unmount();
  }
});
