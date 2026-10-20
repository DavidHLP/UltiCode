import { flushPromises, shallowMount } from "@vue/test-utils";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import BookmarksView from "../BookmarksView.vue";
import RetryButton from "@/components/common/loading/RetryButton.vue";
import BookmarkFolderList from "@/components/bookmark/BookmarkFolderList.vue";

const { loadFolders } = vi.hoisted(() => ({ loadFolders: vi.fn() }));
vi.mock("@/stores/bookmark", () => ({
  useBookmarkStore: () => ({
    folders: [],
    selectedFolder: null,
    selectedFolderId: null,
    selectedFolderDetails: null,
    isLoading: false,
    loadFolders,
  }),
}));
vi.mock("vue-i18n", async (original) => ({
  ...(await original<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => key }),
}));
beforeEach(() => {
  vi.clearAllMocks();
  vi.spyOn(console, "error").mockImplementation(() => {});
});
afterEach(() => vi.restoreAllMocks());

it("replaces a failed collection read with retry instead of successful empty folders", async () => {
  loadFolders
    .mockRejectedValueOnce(new Error("Network Error"))
    .mockResolvedValueOnce(undefined);
  const wrapper = shallowMount(BookmarksView, {
    global: { renderStubDefaultSlot: true },
  });
  try {
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(true);
    expect(wrapper.text()).toContain("personal.bookmarks.loadError");
    expect(wrapper.findComponent(BookmarkFolderList).exists()).toBe(false);
    wrapper.getComponent(RetryButton).vm.$emit("retry");
    await flushPromises();
    expect(loadFolders).toHaveBeenCalledTimes(2);
    expect(loadFolders).toHaveBeenLastCalledWith(true);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.findComponent(BookmarkFolderList).exists()).toBe(true);
  } finally {
    wrapper.unmount();
  }
});
