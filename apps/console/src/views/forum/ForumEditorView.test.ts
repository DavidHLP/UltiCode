import { flushPromises, shallowMount } from "@vue/test-utils";
import { createI18n } from "vue-i18n";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ForumEditorView from "./ForumEditorView.vue";
import enForum from "@/i18n/locales/en-US/forum";
import {
  fetchForumPost,
  fetchForumTags,
  fetchForumCommunities,
  updateForumPost,
} from "@/api/forum";

vi.mock("vue-router", () => ({
  useRoute: () => ({ params: { postId: "post-1" } }),
  useRouter: () => ({ back: vi.fn(), push: vi.fn() }),
}));
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ isAuthenticated: true }),
}));
vi.mock("@/components/markdown", () => ({
  MarkdownEdit: { template: "<div />" },
  MarkdownView: { template: "<div />" },
}));
vi.mock("@/api/forum", () => ({
  fetchForumCommunities: vi.fn(),
  fetchForumTags: vi.fn(),
  fetchForumPost: vi.fn(),
  createForumPost: vi.fn(),
  updateForumPost: vi.fn(),
}));

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchForumCommunities).mockResolvedValue([]);
  vi.mocked(fetchForumTags).mockResolvedValue([
    { id: "ftag-interview", name: "面试", slug: "interview", usageCount: 1 },
    { id: "ftag-career", name: "求职", slug: "career", usageCount: 1 },
  ]);
  vi.mocked(fetchForumPost).mockResolvedValue({
    id: "post-1",
    title: "Existing post",
    createdAt: "2026-01-01",
    excerpt: "Existing content",
    community: {
      id: "community-1",
      name: "General",
      slug: "general",
      description: "General discussion",
      members: 1,
      online: 1,
    },
    tags: ["ftag-interview", "求职", "unknown-tag"],
  });
  vi.mocked(updateForumPost).mockImplementation(async () =>
    fetchForumPost("post-1"),
  );
});

function mountEditor() {
  return shallowMount(ForumEditorView, {
    global: {
      plugins: [
        createI18n({
          legacy: false,
          locale: "en-US",
          messages: { "en-US": { forum: enForum } },
        }),
      ],
      renderStubDefaultSlot: true,
      stubs: {
        Button: { template: "<button><slot /></button>" },
        Separator: true,
      },
    },
  });
}

describe("forum editor existing tags", () => {
  it("shows names and selected state for both stored IDs and names", async () => {
    const view = mountEditor();
    try {
      await flushPromises();
      const tag = view
        .findAll("button")
        .find((button) => button.text() === "面试")!;
      expect(tag.attributes("aria-pressed")).toBe("true");
      expect(view.find('[aria-label="Remove tag 面试"]').exists()).toBe(true);
      expect(view.find('[aria-label="Remove tag 求职"]').exists()).toBe(true);
      expect(view.find('[aria-label="Remove tag unknown-tag"]').exists()).toBe(
        true,
      );
      await tag.trigger("click");
      expect(tag.attributes("aria-pressed")).toBe("false");
      expect(view.find('[aria-label="Remove tag 面试"]').exists()).toBe(false);
      expect(updateForumPost).not.toHaveBeenCalled();
    } finally {
      view.unmount();
    }
  });

  it("preserves stored tag values and content in the existing update payload", async () => {
    const view = mountEditor();
    try {
      await flushPromises();
      await view
        .findAll("button")
        .find((button) => button.text() === enForum.post.updateButton)!
        .trigger("click");
      await flushPromises();
      expect(updateForumPost).toHaveBeenCalledWith(
        "post-1",
        expect.objectContaining({
          tags: ["ftag-interview", "求职", "unknown-tag"],
          excerpt: "Existing content",
          body: "Existing content",
        }),
      );
    } finally {
      view.unmount();
    }
  });
});
