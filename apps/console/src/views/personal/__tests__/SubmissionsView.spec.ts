import { flushPromises, shallowMount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import SubmissionsView from "../SubmissionsView.vue";
import RetryButton from "@/components/common/loading/RetryButton.vue";
import { fetchUserSubmissions } from "@/api/submission";

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ fetchCurrentUserId: () => "test-user" }),
}));
vi.mock("@/api/submission", () => ({
  fetchUserSubmissions: vi.fn().mockResolvedValue({ items: [] }),
}));
vi.mock("vue-router", async (original) => ({
  ...(await original<typeof import("vue-router")>()),
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("vue-i18n", async (original) => ({
  ...(await original<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key, locale: "zh-CN" }),
}));

describe("personal submissions empty states", () => {
  it("distinguishes search misses from an account with no submissions", async () => {
    const wrapper = shallowMount(SubmissionsView, {
      global: {
        renderStubDefaultSlot: true,
        stubs: {
          DataTable: { template: '<div><slot name="empty" /></div>' },
        },
      },
    });
    try {
      await flushPromises();
      expect(wrapper.text()).toContain(
        "personal.submissions.noSubmissionsDesc",
      );
      await wrapper
        .findComponent({ name: "DataTableToolbar" })
        .vm.$emit("update:modelValue", "missing");
      await flushPromises();
      expect(wrapper.text()).toContain("common.search.noResults");
      expect(wrapper.text()).not.toContain(
        "personal.submissions.noSubmissionsDesc",
      );
      expect(wrapper.text()).not.toContain("personal.submissions.startSolving");
    } finally {
      wrapper.unmount();
    }
  });
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, "error").mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

it("shows retryable read failure instead of a successful empty or search result", async () => {
  vi.mocked(fetchUserSubmissions).mockRejectedValueOnce(
    new Error("Network Error"),
  );
  const wrapper = shallowMount(SubmissionsView, {
    global: {
      renderStubDefaultSlot: true,
      stubs: { DataTable: { template: '<div><slot name="empty" /></div>' } },
    },
  });
  try {
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("personal.submissions.loadError");
    expect(wrapper.text()).not.toContain(
      "personal.submissions.noSubmissionsDesc",
    );
    expect(wrapper.text()).not.toContain("personal.submissions.startSolving");
    expect(wrapper.findComponent({ name: "DataTableToolbar" }).exists()).toBe(
      true,
    );
    wrapper.getComponent(RetryButton).vm.$emit("retry");
    await flushPromises();
    expect(fetchUserSubmissions).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain("personal.submissions.noSubmissionsDesc");
  } finally {
    wrapper.unmount();
  }
});
