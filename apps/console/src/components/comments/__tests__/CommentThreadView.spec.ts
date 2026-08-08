import { describe, it, expect, vi, beforeEach } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";

const fetchForumThreadMock = vi.fn();

// Mock the established Forum thread delivery seam. The previous implementation
// reached past it with a raw fetch() and read the wrong wire shape, so every
// test here asserts behavior that crosses this seam.
vi.mock("@/api/forum", () => ({
  fetchForumThread: (...args: unknown[]) => fetchForumThreadMock(...args),
}));

vi.mock("vue-router", () => ({
  useRoute: () => ({ params: { id: "post-123" } }),
}));

vi.mock("vue-i18n", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-i18n")>();
  return {
    ...actual,
    useI18n: () => ({ t: (key: string) => key }),
  };
});

vi.mock("lucide-vue-next", () => ({
  Loader2: { name: "Loader2", template: "<i />" },
  MessageSquare: { name: "MessageSquare", template: "<i />" },
}));

import CommentThreadView from "../CommentThreadView.vue";

const now = () => new Date().toISOString();

const threadWith = (comments: unknown[]) => ({
  id: "post-123",
  title: "title",
  createdAt: now(),
  author: { id: "u1", username: "alice" },
  comments,
});

const comment = (id: string, username: string) => ({
  id,
  body: `body-${id}`,
  createdAt: now(),
  likes: 0,
  dislikes: 0,
  userVote: 0,
  author: { id: `u-${username}`, username },
});

const mountView = () =>
  mount(CommentThreadView, {
    global: {
      stubs: {
        CommentNode: {
          name: "CommentNode",
          template: '<div class="comment-node" />',
        },
      },
    },
  });

beforeEach(() => {
  vi.clearAllMocks();
});

describe("CommentThreadView", () => {
  it("loads the thread via the fetchForumThread seam, not a raw fetch", async () => {
    fetchForumThreadMock.mockResolvedValueOnce(threadWith([]));

    mountView();
    await flushPromises();

    expect(fetchForumThreadMock).toHaveBeenCalledTimes(1);
    expect(fetchForumThreadMock).toHaveBeenCalledWith("post-123");
  });

  it("shows the loading affordance before the seam resolves", async () => {
    // Hold the seam pending so the component stays in its owned loading state.
    fetchForumThreadMock.mockReturnValueOnce(new Promise(() => {}));

    const wrapper = mountView();
    // Intentionally do not flush — assert the mount-time loading state.
    expect(wrapper.text()).toContain("forum.comments.loading");
  });

  it("renders comments lifted onto the ForumThread seam", async () => {
    // Regression: the old raw-fetch path read data.comments from the top level
    // of the raw { post, comments } body, where it is undefined, so the comment
    // thread never rendered. fetchForumThread normalizes the post and carries
    // comments at the top level, so the tree now builds and renders.
    fetchForumThreadMock.mockResolvedValueOnce(
      threadWith([comment("c1", "bob"), comment("c2", "carol")]),
    );

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.findAll(".comment-node")).toHaveLength(2);
  });

  it("renders the empty state when the thread has no comments", async () => {
    fetchForumThreadMock.mockResolvedValueOnce(threadWith([]));

    const wrapper = mountView();
    await flushPromises();

    expect(wrapper.text()).toContain("forum.comments.silence");
    expect(wrapper.findAll(".comment-node")).toHaveLength(0);
  });

  it("does not crash and renders no comments when the seam call fails", async () => {
    // The view currently falls through to its empty/silence branch on failure
    // (pre-existing behavior inherited from the raw-fetch version). Assert only
    // that it does not crash and renders nothing here — a dedicated error state
    // is tracked as a follow-up, out of scope for this seam-routing change.
    fetchForumThreadMock.mockRejectedValueOnce(new Error("network error"));

    const wrapper = mountView();
    await flushPromises();

    expect(fetchForumThreadMock).toHaveBeenCalledTimes(1);
    expect(wrapper.findAll(".comment-node")).toHaveLength(0);
  });
});
