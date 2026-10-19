import { flushPromises, shallowMount } from "@vue/test-utils";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import ForumPostsView from "../ForumPostsView.vue";
import RetryButton from "@/components/common/loading/RetryButton.vue";
import { fetchMyForumPosts } from "@/api/forum";
import { DropdownMenuSeparator } from "@ulticode/design-system/ui/dropdown-menu";

vi.mock("@/api/forum", () => ({
  fetchMyForumPosts: vi.fn(),
  deleteForumPost: vi.fn(),
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

it("shows a persistent forum-specific failure and retries before displaying valid empty posts", async () => {
  vi.mocked(fetchMyForumPosts)
    .mockRejectedValueOnce(new Error("Network Error"))
    .mockResolvedValueOnce({
      items: [],
      total: 0,
      page: 1,
      limit: 10,
      totalPages: 0,
    });
  const wrapper = shallowMount(ForumPostsView, {
    global: { renderStubDefaultSlot: true },
  });
  try {
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("personal.forumPosts.loadError");
    expect(wrapper.text()).not.toContain("personal.forumPosts.noPosts");
    expect(wrapper.text()).not.toContain("personal.forumPosts.createFirst");
    wrapper.getComponent(RetryButton).vm.$emit("retry");
    await flushPromises();
    expect(fetchMyForumPosts).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain("personal.forumPosts.noPosts");
  } finally {
    wrapper.unmount();
  }
});

it("resolves the shared separator in a nonempty post action menu", async () => {
  vi.mocked(fetchMyForumPosts).mockResolvedValueOnce({
    items: [
      {
        id: "unit-post",
        title: "Post menu",
        createdAt: "2026-10-01T00:00:00Z",
      },
    ],
    total: 1,
    page: 1,
    limit: 10,
    totalPages: 1,
  });
  const wrapper = shallowMount(ForumPostsView, {
    global: { renderStubDefaultSlot: true },
  });
  try {
    await flushPromises();
    expect(wrapper.findComponent(DropdownMenuSeparator).exists()).toBe(true);
  } finally {
    wrapper.unmount();
  }
});
