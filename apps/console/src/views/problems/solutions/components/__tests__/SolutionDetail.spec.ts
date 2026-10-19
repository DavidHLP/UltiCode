import { flushPromises, mount, shallowMount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ref } from "vue";
import SolutionDetail from "../SolutionDetail.vue";
import type { SolutionFeedItem } from "@/types/solution";

const { routerPush, scrollToHeading } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  scrollToHeading: vi.fn(),
}));

vi.mock("@/composables/useContentNavigation", () => ({
  useContentNavigation: () => ({
    wrapperRef: ref(null),
    activeHeadingId: ref(""),
    isWideLayout: ref(false),
    showMobileTOC: ref(false),
    scrollToHeading,
  }),
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: routerPush }),
}));

vi.mock("vue-i18n", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-i18n")>();
  return {
    ...actual,
    useI18n: () => ({ t: (key: string) => key }),
  };
});

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({
    fetchCurrentUserId: () => "different-user",
    isAuthenticated: false,
  }),
}));

vi.mock("@/api/solution", () => ({
  fetchSolutionComments: vi.fn().mockResolvedValue([]),
  createSolutionComment: vi.fn(),
  updateSolutionComment: vi.fn(),
  deleteSolutionComment: vi.fn(),
  recordSolutionView: vi.fn().mockResolvedValue(undefined),
  deleteSolution: vi.fn(),
}));

vi.mock("@/api/vote", () => ({
  VoteTargetType: {
    SOLUTION: "solution",
    SOLUTION_COMMENT: "solution-comment",
  },
  vote: vi.fn(),
}));

vi.mock("vue-sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}));

vi.mock("@/composables/useErrorHandler", () => ({
  useErrorHandler: () => ({ handleError: vi.fn() }),
}));

const item: SolutionFeedItem = {
  id: "solution-1",
  problem_id: "7",
  title: "Two pointers",
  summary: "A solution",
  authorId: "user-1",
  author: {
    id: "user-1",
    username: "wangming",
    name: "王明",
    role: "算法",
  },
  stats: { views: 1, comments: 0, likes: 2, dislikes: 0 },
  score: 2,
  created_at: "2026-08-24T00:00:00Z",
  publishedAt: "2026-08-24T00:00:00Z",
  language: "python",
  tags: [],
  votes: 2,
  views: 1,
  likes: 2,
  content: "content",
};

describe("SolutionDetail", () => {
  beforeEach(() => {
    routerPush.mockClear();
  });

  it("separates metadata and actions into grid rows without shrinking the avatar", () => {
    const wrapper = shallowMount(SolutionDetail, { props: { item } });
    expect(wrapper.get("article > header").classes()).toContain("grid");
    expect(wrapper.get("article > header > .col-span-2").classes()).toContain(
      "flex-wrap",
    );
    expect(wrapper.get("article").classes()).toContain("min-w-0");
  });

  it("portals the outline and restores focus without interrupting heading scroll", async () => {
    vi.stubGlobal(
      "ResizeObserver",
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    );
    const view = mount(SolutionDetail, {
      attachTo: document.body,
      props: { item: { ...item, content: "## First section\n\nBody" } },
      global: {
        stubs: {
          MarkdownView: true,
          CommentThread: true,
          PostActions: true,
          ReportDialog: true,
        },
      },
    });
    try {
      const trigger = view
        .findAll("button")
        .find((b) => b.text() === "forum.sidebar.toc")!;
      await trigger.trigger("click");
      await flushPromises();
      const outline = document.querySelector('[data-slot="popover-content"]');
      expect(outline).not.toBeNull();
      expect(view.element.contains(outline)).toBe(false);
      const focus = vi.spyOn(trigger.element, "focus");
      Array.from(outline!.querySelectorAll("button"))
        .find((b) => b.textContent?.trim() === "First section")!
        .click();
      await flushPromises();
      expect(scrollToHeading).toHaveBeenCalledWith("first-section");
      expect(
        document.querySelector('[data-slot="popover-content"]'),
      ).toBeNull();
      expect(focus).toHaveBeenCalledWith({ preventScroll: true });
      await trigger.trigger("click");
      await flushPromises();
      const outside = view
        .findAll("button")
        .find((b) => b.text() === "forum.actions.report")!;
      outside.element.focus();
      await flushPromises();
      expect(document.activeElement).toBe(outside.element);
      expect(
        document.querySelector('[data-slot="popover-content"]'),
      ).toBeNull();
    } finally {
      view.unmount();
      vi.unstubAllGlobals();
    }
  });

  it("opens the author's public profile from the header avatar", async () => {
    const wrapper = shallowMount(SolutionDetail, {
      props: { item },
      global: { stubs: { Button: false, Primitive: false } },
    });

    await wrapper.get("article > header > button").trigger("click");

    expect(routerPush).toHaveBeenCalledWith({
      name: "public-profile",
      params: { username: "wangming" },
    });
  });

  it("does not navigate the editorial fallback avatar", async () => {
    const wrapper = shallowMount(SolutionDetail, {
      global: { stubs: { Button: false, Primitive: false } },
      props: {
        item: {
          ...item,
          id: "follow-up",
          author: { ...item.author, username: "editorial" },
        },
      },
    });

    const avatarButton = wrapper.get("article > header > button");
    expect(avatarButton.attributes("disabled")).toBeDefined();
    await avatarButton.trigger("click");

    expect(routerPush).not.toHaveBeenCalled();
  });
});
