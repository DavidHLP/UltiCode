import { flushPromises, shallowMount } from "@vue/test-utils";
import { nextTick, reactive, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ForumFeedView from "./ForumFeedView.vue";
import ForumPostCard from "./components/ForumPostCard.vue";
import {
  fetchForumCommunity,
  fetchCommunityPosts,
  fetchForumPosts,
  fetchForumQuickFilters,
} from "@/api/forum";

const route = reactive<{ params: { category?: string } }>({ params: {} });
const locale = ref("zh-CN");
vi.mock("vue-router", () => ({
  RouterLink: { template: "<a><slot /></a>" },
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock("vue-i18n", async (original) => ({
  ...(await original<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => `${locale.value}:${key}`, locale }),
}));
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ isAuthenticated: true }),
}));
vi.mock("@/api/forum", () => ({
  fetchForumCommunity: vi.fn(),
  fetchCommunityPosts: vi.fn(),
  fetchForumCommunities: vi.fn().mockResolvedValue([]),
  fetchForumPosts: vi.fn(),
  fetchForumQuickFilters: vi.fn(),
}));

const emptyPage = { items: [], total: 0, totalPages: 0, page: 1, pageSize: 20 };
let wrapper: ReturnType<typeof shallowMount> | undefined;
function mountFeed(filter?: string) {
  wrapper = shallowMount(ForumFeedView, {
    props: { filter },
    global: {
      renderStubDefaultSlot: true,
      stubs: { Button: { template: "<button><slot /></button>" } },
    },
  });
  return wrapper;
}

beforeEach(() => {
  vi.resetAllMocks();
  locale.value = "zh-CN";
  route.params = { category: "general" };
  vi.spyOn(console, "error").mockImplementation(() => {});
  vi.mocked(fetchForumCommunity).mockResolvedValue({
    community: null,
    rules: [],
    links: [],
  });
  vi.mocked(fetchCommunityPosts).mockResolvedValue(emptyPage);
  vi.mocked(fetchForumPosts).mockResolvedValue(emptyPage);
  vi.mocked(fetchForumQuickFilters).mockResolvedValue([
    { value: "hot", label: "Hot" },
  ]);
});
afterEach(() => {
  wrapper?.unmount();
  vi.restoreAllMocks();
});

describe("forum feed loading states", () => {
  it("labels the explore route without changing its sort request or available options", async () => {
    route.params = {};
    const view = mountFeed("explore");
    await flushPromises();
    const value = view.findComponent({ name: "SelectValue" });
    expect(value.text()).toBe("zh-CN:sidebar.forum.explore");
    expect(fetchForumPosts).toHaveBeenCalledWith({
      sortBy: "explore",
      page: 1,
      pageSize: 20,
    });
    expect(view.findAllComponents({ name: "SelectItem" })).toHaveLength(1);
    locale.value = "en-US";
    await nextTick();
    expect(value.text()).toBe("en-US:sidebar.forum.explore");
    view.findComponent({ name: "Select" }).vm.$emit("update:modelValue", "hot");
    await flushPromises();
    expect(value.text()).toBe("en-US:forum.sort.hot");
    expect(fetchForumPosts).toHaveBeenLastCalledWith({
      sortBy: "hot",
      page: 1,
      pageSize: 20,
    });
  });

  it("distinguishes no search matches from a genuinely empty feed", async () => {
    const view = mountFeed();
    await flushPromises();
    view
      .findComponent({ name: "Input" })
      .vm.$emit("update:modelValue", "no match");
    await nextTick();
    expect(view.get('[role="status"]').text()).toContain(
      "common.search.noResults",
    );
    expect(view.get('[role="status"]').text()).not.toContain(
      "forum.list.noPosts",
    );
    expect(
      view.findComponent({ name: "SelectTrigger" }).attributes("aria-label"),
    ).toBe("zh-CN:forum.list.sort");
    view.findComponent({ name: "Input" }).vm.$emit("update:modelValue", "");
    await nextTick();
    expect(view.get('[role="status"]').text()).toContain("forum.list.noPosts");
  });

  it("updates the selected sort label on language changes without opening its menu", async () => {
    const view = mountFeed();
    await flushPromises();
    const value = view.findComponent({ name: "SelectValue" });
    expect(value.text()).toBe("zh-CN:forum.sort.hot");
    locale.value = "en-US";
    await nextTick();
    expect(value.text()).toBe("en-US:forum.sort.hot");
  });

  it("loads sorting options when entering a community directly", async () => {
    const view = mountFeed();
    await flushPromises();
    expect(fetchForumQuickFilters).toHaveBeenCalled();
    expect(view.text()).toContain("forum.sort.hot");
    expect(view.text()).toContain("forum.list.noPosts");
  });

  it.each(["general", undefined])(
    "distinguishes a load error from an empty list (%s)",
    async (category) => {
      route.params = { category };
      vi.mocked(fetchForumCommunity).mockRejectedValueOnce(
        new Error("unavailable"),
      );
      vi.mocked(fetchForumPosts).mockRejectedValueOnce(
        new Error("unavailable"),
      );
      const view = mountFeed();
      await flushPromises();
      expect(view.get('[role="alert"]').text()).toContain(
        "forum.list.loadFailed",
      );
      expect(view.text()).not.toContain("forum.list.noPosts");
    },
  );

  it("retries a failed community load and clears its error", async () => {
    vi.mocked(fetchForumCommunity).mockRejectedValueOnce(
      new Error("unavailable"),
    );
    const view = mountFeed();
    await flushPromises();
    await view.get('[role="alert"] button').trigger("click");
    await flushPromises();
    expect(fetchForumCommunity).toHaveBeenCalledTimes(2);
    expect(view.find('[role="alert"]').exists()).toBe(false);
    expect(view.text()).toContain("forum.list.noPosts");
  });
});

describe("forum post card markdown excerpt", () => {
  it("clips block markdown without line-clamp layout", () => {
    wrapper = shallowMount(ForumPostCard, {
      props: {
        post: {
          id: "post-1",
          title: "Markdown preview",
          createdAt: "2026-10-18T00:00:00Z",
          excerpt: "# Heading\n\nParagraph\n\n- item",
        },
      },
    });

    const excerpt = wrapper.get(".markdown-view > div");
    expect(excerpt.classes()).toContain("max-h-[3lh]");
    expect(excerpt.classes()).toContain("overflow-hidden");
    expect(excerpt.classes()).not.toContain("line-clamp-3");
  });
});
