import { DOMWrapper, mount } from "@vue/test-utils";
import { flushPromises } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  problems: vi.fn(),
  submissions: vi.fn(),
  submission: vi.fn(),
  solutions: vi.fn(),
  comments: vi.fn(),
  posts: vi.fn(),
  thread: vi.fn(),
}));
vi.mock("@/api/problem", () => ({ fetchProblems: mocks.problems }));
vi.mock("@/api/submission", () => ({
  fetchUserSubmissions: mocks.submissions,
  fetchSubmission: mocks.submission,
}));
vi.mock("@/api/solution", () => ({
  fetchSolutionFeed: mocks.solutions,
  fetchSolutionComments: mocks.comments,
}));
vi.mock("@/api/forum", () => ({
  fetchForumPosts: mocks.posts,
  fetchForumThread: mocks.thread,
}));
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ user: { id: "owner" } }),
}));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));
import AgentContextPicker from "../components/AgentContextPicker.vue";

const submission = {
  id: "11111111-1111-4111-8111-111111111111",
  problem_id: 7,
  status: "Accepted",
  language: "java",
  created_at: "2026-01-01",
  contestSubmission: false,
  user: { id: "owner" },
  problem: { id: 7, title: "Two Sum" },
};
const body = () => new DOMWrapper(document.body);
const button = (text: string) =>
  body()
    .findAll("button")
    .find((item) => item.text() === text)!;

describe("AgentContextPicker", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.problems.mockResolvedValue({ items: [{ id: 7, title: "Two Sum" }] });
    mocks.submissions.mockResolvedValue({ items: [submission], total: 1 });
    mocks.submission.mockResolvedValue(submission);
    mocks.solutions.mockResolvedValue({
      items: [{ id: "solution-1", title: "Solution one" }],
    });
    mocks.comments.mockResolvedValue([
      {
        id: "comment-1",
        content: "<script>alert(1)</script> Check a boundary",
      },
    ]);
  });
  afterEach(() => {
    document.body.innerHTML = "";
  });

  it("selects an owned submission and comment references without executing markup or starting analysis", async () => {
    const wrapper = mount(AgentContextPicker, {
      attachTo: document.body,
      props: {
        submissionId: "",
        context: { includeProblem: false, comments: [] },
        hasConversation: false,
      },
    });
    await wrapper.get("button").trigger("click");
    await flushPromises();
    await button("#7 · Two Sum").trigger("click");
    await flushPromises();
    expect(mocks.submissions).toHaveBeenCalledWith({ problemId: 7, page: 1 });
    await body()
      .findAll("button")
      .find((item) => item.text().includes("java · Accepted"))!
      .trigger("click");
    await flushPromises();
    await body().get('[role="checkbox"]').trigger("click");
    await body()
      .findAll('[role="tab"]')
      .find((item) => item.text().includes("agent.context.solutionComments"))!
      .trigger("keydown", { key: "Enter" });
    await flushPromises();
    await button("Solution one").trigger("click");
    await flushPromises();
    expect(body().text()).toContain("<script>alert(1)</script>");
    expect(body().find("script").exists()).toBe(false);
    await body().get('[role="tabpanel"] [role="checkbox"]').trigger("click");
    await button("agent.context.apply").trigger("click");
    await flushPromises();
    expect(wrapper.emitted("select")?.[0]).toEqual([
      submission,
      {
        includeProblem: true,
        comments: [{ solutionId: "solution-1", commentId: "comment-1" }],
      },
    ]);
    wrapper.unmount();
  });

  it("selects forum replies and enforces the shared three-comment limit", async () => {
    mocks.posts.mockResolvedValue({
      items: [{ id: "post-1", title: "Forum post" }],
      total: 1,
    });
    mocks.thread.mockResolvedValue({
      comments: [
        {
          id: "parent",
          body: "Parent",
          replies: [1, 2, 3].map((id) => ({
            id: `reply-${id}`,
            body: `Reply ${id}`,
          })),
        },
      ],
    });
    const wrapper = mount(AgentContextPicker, {
      attachTo: document.body,
      props: {
        submissionId: submission.id,
        context: { includeProblem: false, comments: [] },
        hasConversation: true,
      },
    });
    await wrapper.get("button").trigger("click");
    await flushPromises();
    await body()
      .findAll('[role="tab"]')
      .find((item) => item.text() === "agent.context.forumComments")!
      .trigger("keydown", { key: "Enter" });
    await flushPromises();
    await button("Forum post").trigger("click");
    await flushPromises();
    const boxes = body().findAll('[role="tabpanel"] [role="checkbox"]');
    expect(boxes).toHaveLength(4);
    for (const box of boxes.slice(0, 3)) await box.trigger("click");
    expect(boxes[3].attributes("disabled")).toBeDefined();
    await button("agent.context.apply").trigger("click");
    await flushPromises();
    expect(wrapper.emitted("select")?.[0]?.[1]).toEqual({
      includeProblem: false,
      comments: [
        { postId: "post-1", commentId: "parent" },
        { postId: "post-1", commentId: "reply-1" },
        { postId: "post-1", commentId: "reply-2" },
      ],
    });
    wrapper.unmount();
  });

  it("rejects a submission owned by another account", async () => {
    mocks.submission.mockResolvedValue({
      ...submission,
      user: { id: "other" },
    });
    const wrapper = mount(AgentContextPicker, {
      attachTo: document.body,
      props: {
        submissionId: submission.id,
        context: { includeProblem: false, comments: [] },
        hasConversation: true,
      },
    });
    await wrapper.get("button").trigger("click");
    await flushPromises();
    expect(body().get('[role="alert"]').text()).toBe(
      "agent.context.ineligible",
    );
    expect(button("agent.context.apply").attributes("disabled")).toBeDefined();
    expect(wrapper.emitted("select")).toBeUndefined();
    wrapper.unmount();
  });

  it("retries a failed lookup without losing the selected submission", async () => {
    mocks.submission
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce(submission);
    const wrapper = mount(AgentContextPicker, {
      attachTo: document.body,
      props: {
        submissionId: submission.id,
        context: { includeProblem: false, comments: [] },
        hasConversation: true,
      },
    });
    await wrapper.get("button").trigger("click");
    await flushPromises();
    expect(body().get('[role="alert"]').text()).toBe(
      "agent.context.loadFailed",
    );
    await button("agent.retry").trigger("click");
    await flushPromises();
    expect(
      button("agent.context.apply").attributes("disabled"),
    ).toBeUndefined();
    expect(mocks.submission).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });
});
