import { flushPromises, mount } from "@vue/test-utils";
import { computed, reactive, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { SubmissionRecord } from "@/types/submission";

const mocks = vi.hoisted(() => ({
  getCapabilities: vi.fn(),
  fetchUserSubmissions: vi.fn(),
  fetchSubmission: vi.fn(),
  auth: null as { user: { id: string } | null } | null,
  route: { query: {} as Record<string, string> },
  replace: vi.fn(),
  workspace: null as object | null,
}));

vi.mock("@/api/agent", () => ({
  agentApi: { getCapabilities: mocks.getCapabilities },
}));
vi.mock("@/api/problem", () => ({ fetchProblems: vi.fn() }));
vi.mock("@/api/forum", () => ({
  fetchForumPosts: vi.fn(),
  fetchForumThread: vi.fn(),
}));
vi.mock("@/api/solution", () => ({
  fetchSolutionFeed: vi.fn(),
  fetchSolutionComments: vi.fn(),
}));
vi.mock("@/stores/auth", () => ({
  useAuthStore: () => mocks.auth,
}));
vi.mock("@/api/submission", () => ({
  fetchUserSubmissions: mocks.fetchUserSubmissions,
  fetchSubmission: mocks.fetchSubmission,
}));
vi.mock("@/composables/useAgentConversations", () => ({
  useAgentConversations: () => mocks.workspace,
}));
vi.mock("vue-router", () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace }),
}));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));

import AgentConversationWorkspace from "../AgentConversationWorkspace.vue";

const submissionId = "12345678-1234-1234-1234-123456789abc";
const conversationId = "abcdef12-1234-1234-1234-123456789abc";

function submission(
  overrides: Partial<SubmissionRecord> = {},
): SubmissionRecord {
  return {
    id: submissionId,
    problem_id: 1,
    problem: { id: 1, title: "Recent practice problem", slug: "practice" },
    status: "Accepted",
    language: "java",
    runtime: 10,
    memory: 20,
    created_at: "2026-01-01T00:00:00Z",
    user: { id: "owner", username: "learner" },
    contestSubmission: false,
    ...overrides,
  };
}

function createWorkspace() {
  const state = {
    conversations: ref([]),
    conversation: ref(null),
    turns: ref([]),
    events: ref([]),
    sourceSubmissionId: ref(""),
    error: ref(""),
    loadingHistory: ref(false),
    loadingConversation: ref(false),
    busy: ref(false),
    canceling: ref(false),
    deleting: ref(false),
    hasMoreConversations: ref(false),
    hasMoreTurns: ref(false),
    currentTurn: computed(() => null),
    loadConversations: vi.fn().mockResolvedValue(undefined),
    openConversation: vi.fn(async () => undefined),
    loadOlderTurns: vi.fn(),
    sendTurn: vi.fn().mockResolvedValue(undefined),
    retryTurn: vi.fn().mockResolvedValue(undefined),
    cancelTurn: vi.fn(),
    deleteCurrentConversation: vi.fn().mockResolvedValue(true),
    startNewConversation: vi.fn((id = "") => {
      state.sourceSubmissionId.value = id;
      state.conversation.value = null;
      state.turns.value = [];
      return true;
    }),
  };
  return state;
}

describe("AgentConversationWorkspace", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "ResizeObserver",
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    );
    vi.stubGlobal(
      "matchMedia",
      vi.fn((query: string) => ({
        matches: query === "(min-width: 1024px)",
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    );
    vi.clearAllMocks();
    mocks.route.query = { submissionId };
    mocks.workspace = createWorkspace();
    mocks.auth = reactive({ user: { id: "owner" } });
    mocks.fetchUserSubmissions.mockReset().mockResolvedValue({ items: [] });
    mocks.fetchSubmission.mockReset().mockResolvedValue(submission());
    mocks.getCapabilities.mockResolvedValue({
      analysisAvailable: true,
      persistenceAvailable: true,
      conversationHistoryAvailable: true,
      sourceMode: "owner_source",
    });
  });

  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it("toggles desktop navigation without changing the conversation or draft", async () => {
    const wrapper = mount(AgentConversationWorkspace, {
      attachTo: document.body,
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    await flushPromises();
    await wrapper.get("textarea").setValue("Keep my question");
    const toggle = wrapper.get('[aria-controls="agent-desktop-history"]');
    expect(toggle.attributes("aria-expanded")).toBe("true");
    wrapper.findComponent({ name: "ResizablePanel" }).vm.$emit("resize", 27);
    await toggle.trigger("click");
    await flushPromises();
    const reopen = wrapper.get('[aria-controls="agent-desktop-history"]');
    expect(reopen.attributes("aria-expanded")).toBe("false");
    expect(document.activeElement).toBe(reopen.element);
    expect(wrapper.find("#agent-desktop-history").exists()).toBe(false);
    expect(wrapper.get('[role="separator"]').attributes("style")).toContain(
      "display: none",
    );
    await reopen.trigger("click");
    await flushPromises();
    expect(document.activeElement).toBe(
      wrapper.get('[aria-controls="agent-desktop-history"]').element,
    );
    expect(
      wrapper.findComponent({ name: "ResizablePanel" }).props("defaultSize"),
    ).toBe(27);
    expect(wrapper.find('[role="separator"]').exists()).toBe(true);
    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe(
      "Keep my question",
    );
    expect(
      (mocks.workspace as ReturnType<typeof createWorkspace>).sendTurn,
    ).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("filters loaded history without making another request", async () => {
    const state = mocks.workspace as ReturnType<typeof createWorkspace>;
    state.conversations.value = [
      {
        conversationId,
        title: "Complexity review",
        lastQuestion: "Explain memory",
        updatedAt: 1792750312.355659,
        turnCount: 1,
      },
    ];
    const wrapper = mount(AgentConversationWorkspace);
    await flushPromises();
    await wrapper.get("input").setValue("MEMORY");
    expect(wrapper.text()).toContain("Complexity review");
    expect(wrapper.text()).toContain("2026");
    expect(wrapper.text()).not.toContain("1970");
    await wrapper.get("input").setValue("unmatched");
    expect(wrapper.text()).not.toContain("Complexity review");
    expect(wrapper.text()).toContain("agent.workspace.noSearchResults");
    await wrapper
      .get('[aria-label="agent.workspace.clearSearch"]')
      .trigger("click");
    expect((wrapper.get("input").element as HTMLInputElement).value).toBe("");
    expect(wrapper.text()).toContain("Complexity review");
    await wrapper.get("input").setValue("unmatched");
    await wrapper.get("input").trigger("keydown", { key: "Escape" });
    expect(wrapper.text()).toContain("Complexity review");
    expect((wrapper.get("input").element as HTMLInputElement).value).toBe("");
    expect(state.loadConversations).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it("fills suggestions without sending and ignores Enter during IME composition", async () => {
    const wrapper = mount(AgentConversationWorkspace);
    await flushPromises();
    const state = mocks.workspace as ReturnType<typeof createWorkspace>;
    const suggestion = wrapper
      .findAll("button")
      .find((button) => button.text() === "agent.promptComplexity");
    await suggestion!.trigger("click");
    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe(
      "agent.promptComplexity",
    );
    expect(state.sendTurn).not.toHaveBeenCalled();
    await wrapper.get('[role="checkbox"]').trigger("click");
    await wrapper
      .get("textarea")
      .trigger("keydown", { key: "Enter", isComposing: true });
    expect(state.sendTurn).not.toHaveBeenCalled();
    await wrapper
      .get("textarea")
      .trigger("keydown", { key: "Enter", shiftKey: true });
    expect(state.sendTurn).not.toHaveBeenCalled();
    await wrapper.get("textarea").trigger("keydown", { key: "Enter" });
    expect(state.sendTurn).toHaveBeenCalledWith("agent.promptComplexity", true);
    wrapper.unmount();
  });

  it("keeps the unbound state focused and selects a verified submission without sending", async () => {
    mocks.route.query = {};
    mocks.fetchUserSubmissions.mockResolvedValue({
      items: [{ id: submissionId }],
    });
    const wrapper = mount(AgentConversationWorkspace, {
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    await flushPromises();
    const state = mocks.workspace as ReturnType<typeof createWorkspace>;

    expect(wrapper.find("textarea").exists()).toBe(false);
    expect(
      wrapper
        .findAll("h2")
        .filter((heading) => heading.text() === "agent.workspace.emptyTitle"),
    ).toHaveLength(1);
    expect(mocks.fetchSubmission).toHaveBeenCalledWith(submissionId);
    const recent = wrapper
      .findAll("button")
      .find((button) => button.text().includes("Recent practice problem"));
    expect(recent).toBeDefined();
    await recent!.trigger("click");
    await flushPromises();

    expect(state.startNewConversation).toHaveBeenCalledWith(submissionId);
    expect(mocks.replace).toHaveBeenCalledWith({
      name: "agent-workspace",
      query: { submissionId },
    });
    expect(wrapper.find("textarea").exists()).toBe(true);
    expect(state.sendTurn).not.toHaveBeenCalled();
    expect(wrapper.get('[role="checkbox"]').attributes("aria-checked")).toBe(
      "false",
    );
    wrapper.unmount();
  });

  it.each([
    ["contest", { contestSubmission: true }],
    ["another user's", { user: { id: "other-owner", username: "other" } }],
    ["unqualified", { contestSubmission: undefined }],
  ] satisfies [string, Partial<SubmissionRecord>][])(
    "excludes %s submissions from the recent list",
    async (_label, overrides) => {
      mocks.route.query = {};
      mocks.fetchUserSubmissions.mockResolvedValue({
        items: [{ id: submissionId }],
      });
      mocks.fetchSubmission.mockResolvedValue(submission(overrides));
      const wrapper = mount(AgentConversationWorkspace, {
        global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
      });
      await flushPromises();

      expect(wrapper.text()).not.toContain("Recent practice problem");
      expect(wrapper.text()).toContain("agent.workspace.noRecentSubmissions");
      expect(wrapper.find("textarea").exists()).toBe(false);
      wrapper.unmount();
    },
  );

  it("shows a recent-submissions failure and retries instead of reporting an empty list", async () => {
    mocks.route.query = {};
    mocks.fetchUserSubmissions.mockRejectedValueOnce(new Error("unavailable"));
    const wrapper = mount(AgentConversationWorkspace, {
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    await flushPromises();

    expect(wrapper.get('[role="alert"]').text()).toContain(
      "agent.workspace.submissionsError",
    );
    expect(wrapper.text()).not.toContain("agent.workspace.noRecentSubmissions");
    mocks.fetchUserSubmissions.mockResolvedValue({
      items: [{ id: submissionId }],
    });
    await wrapper.get('[role="alert"] button').trigger("click");
    await flushPromises();

    expect(mocks.fetchUserSubmissions).toHaveBeenCalledTimes(2);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.text()).toContain("Recent practice problem");
    wrapper.unmount();
  });

  it("ignores submission details that arrive after the account changes", async () => {
    mocks.route.query = {};
    const other = submission({
      id: "23456789-1234-1234-1234-123456789abc",
      problem: { id: 2, title: "Current account problem", slug: "current" },
      user: { id: "other-owner", username: "other" },
    });
    let resolveOld!: (value: SubmissionRecord) => void;
    mocks.fetchUserSubmissions
      .mockResolvedValueOnce({ items: [{ id: submissionId }] })
      .mockResolvedValueOnce({ items: [{ id: other.id }] });
    mocks.fetchSubmission
      .mockImplementationOnce(
        () =>
          new Promise<SubmissionRecord>((resolve) => {
            resolveOld = resolve;
          }),
      )
      .mockResolvedValueOnce(other);
    const wrapper = mount(AgentConversationWorkspace, {
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    await flushPromises();

    mocks.auth!.user = { id: "other-owner" };
    await flushPromises();
    expect(wrapper.text()).toContain("Current account problem");
    resolveOld(submission());
    await flushPromises();

    expect(wrapper.text()).toContain("Current account problem");
    expect(wrapper.text()).not.toContain("Recent practice problem");
    expect(mocks.fetchUserSubmissions).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });

  it("requires fresh source consent for each sent turn", async () => {
    const wrapper = mount(AgentConversationWorkspace, {
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    await flushPromises();
    const state = mocks.workspace as ReturnType<typeof createWorkspace>;
    expect(state.sourceSubmissionId.value).toBe(submissionId);

    const send = wrapper.find('button[type="submit"]');
    expect(send.attributes("disabled")).toBeDefined();
    await wrapper.get('[role="checkbox"]').trigger("click");
    await wrapper.find("textarea").setValue("Explain this result");
    expect(send.attributes("disabled")).toBeUndefined();
    await wrapper.find("form").trigger("submit");
    await flushPromises();
    expect(state.sendTurn).toHaveBeenCalledWith("Explain this result", true);
    expect(
      (wrapper.find("textarea").element as HTMLTextAreaElement).value,
    ).toBe("Explain this result");
    expect(wrapper.get('[role="checkbox"]').attributes("aria-checked")).toBe(
      "false",
    );
    expect(send.attributes("disabled")).toBeDefined();

    wrapper.unmount();
  });

  it("unlocks fresh consent after canonical completion and keeps a new draft when the old POST settles", async () => {
    const state = mocks.workspace as ReturnType<typeof createWorkspace>;
    let finishOld!: () => void;
    let finishNew!: () => void;
    state.sendTurn.mockImplementationOnce(() => {
      state.busy.value = true;
      return new Promise<void>((resolve) => { finishOld = resolve; });
    }).mockImplementationOnce(() => new Promise<void>((resolve) => { finishNew = resolve; }));
    const wrapper = mount(AgentConversationWorkspace, {
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    await flushPromises();
    await wrapper.get('[role="checkbox"]').trigger("click");
    await wrapper.find("textarea").setValue("First question");
    await wrapper.find("form").trigger("submit");
    await flushPromises();
    state.busy.value = false;
    await flushPromises();
    expect(wrapper.get('[role="checkbox"]').attributes("disabled")).toBeUndefined();
    expect((wrapper.find("textarea").element as HTMLTextAreaElement).value).toBe("");
    await wrapper.get('[role="checkbox"]').trigger("click");
    await wrapper.find("textarea").setValue("New question");
    await wrapper.find("form").trigger("submit");
    await flushPromises();
    finishOld();
    await flushPromises();
    expect((wrapper.find("textarea").element as HTMLTextAreaElement).value).toBe("New question");
    expect(wrapper.get('[role="checkbox"]').attributes("disabled")).toBeDefined();
    finishNew();
    await flushPromises();
    wrapper.unmount();
  });

  it("renders stored answers safely and exposes cancellation for queued turns", async () => {
    const state = mocks.workspace as ReturnType<typeof createWorkspace>;
    state.openConversation.mockImplementation(async () => {
      state.conversation.value = {
        conversationId,
        sourceSubmissionId: submissionId,
        title: "Review submission",
        turnCount: 1,
        lastQuestion: "Explain the result",
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      };
      state.sourceSubmissionId.value = submissionId;
      state.turns.value = [
        {
          turnId: "turn-1",
          conversationId,
          turnIndex: 1,
          runId: "run-1",
          status: "completed",
          question: "Explain the result",
          context: {
            includeProblem: true,
            comments: [{ postId: "fpost-001", commentId: "fcmt-001" }],
          },
          answer: "<script>alert(1)</script>\n\n**Safe conclusion**",
          citations: [
            {
              chunk_id: "source-1",
              doc_id: "submission",
              version: "current",
              source_path: "owner-submission",
              source_position: "lines 1-21",
              access_scope: "owner",
              sample_kind: "owner-submission",
              source_trust: "untrusted-data",
              claim: '<img src=x onerror="alert(1)">',
              text: "PRIVATE_EXCERPT",
            },
          ],
          trace: [
            { tool_name: "get_submission_source", failed: false },
            { tool_name: "search_evidence", failed: true },
          ],
          failureReason: null,
          cancelRequested: false,
          eventSeq: 0,
          createdAt: "2026-01-01T00:00:00Z",
          updatedAt: "2026-01-01T00:00:00Z",
        },
      ];
    });
    mocks.route.query = { conversationId };
    const wrapper = mount(AgentConversationWorkspace, {
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    await flushPromises();

    expect(wrapper.find(".typeset.typeset-docs").text()).toContain(
      "Safe conclusion",
    );
    expect(wrapper.find(".typeset.typeset-docs").html()).not.toContain(
      "<script",
    );
    expect(state.openConversation).toHaveBeenCalledWith(conversationId);
    expect(wrapper.find('[data-slot="bubble-content"]').text()).toContain(
      "Explain the result",
    );
    await wrapper
      .findAll("button")
      .find((button) => button.text().includes("agent.citations"))!
      .trigger("click");
    await wrapper
      .findAll("button")
      .find((button) => button.text().includes("agent.workspace.activity"))!
      .trigger("click");
    await flushPromises();
    expect(
      wrapper.find('[data-slot="trace-collapsible-content"] img').exists(),
    ).toBe(false);
    expect(wrapper.text()).toContain('onerror="alert(1)"');
    expect(wrapper.text()).toContain("agent.workspace.readSource");
    expect(
      wrapper.find('[data-slot="trace-line"][data-status="error"]').text(),
    ).toContain("agent.workspace.searchEvidence");
    expect(wrapper.text()).not.toContain("PRIVATE_EXCERPT");
    await wrapper.get('[role="checkbox"]').trigger("click");
    await wrapper
      .get('[aria-label="agent.workspace.reuseQuestion"]')
      .trigger("click");
    await flushPromises();
    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe(
      "Explain the result",
    );
    expect(wrapper.get('[role="checkbox"]').attributes("aria-checked")).toBe(
      "false",
    );
    expect(wrapper.text()).toContain("agent.context.forumComments");
    expect(state.sendTurn).not.toHaveBeenCalled();
    await wrapper.get('[role="checkbox"]').trigger("click");
    await wrapper
      .findAll("button")
      .find((button) => button.text() === "agent.context.clear")!
      .trigger("click");
    expect(wrapper.get('[role="checkbox"]').attributes("aria-checked")).toBe(
      "false",
    );
    expect(wrapper.text()).not.toContain("agent.context.forumComments");
    await wrapper.get("textarea").setValue("Keep this unsent draft");
    expect(
      wrapper
        .get('[aria-label="agent.workspace.reuseQuestion"]')
        .attributes("disabled"),
    ).toBeDefined();
    await wrapper.get("textarea").setValue("");
    await wrapper
      .findAll("button")
      .find((button) => button.text() === "agent.workspace.followupEdges")!
      .trigger("click");
    expect((wrapper.get("textarea").element as HTMLTextAreaElement).value).toBe(
      "agent.workspace.followupEdges",
    );
    expect(state.sendTurn).not.toHaveBeenCalled();
    await wrapper.get("textarea").setValue("");
    await wrapper
      .get('[aria-label="agent.workspace.conversationActions"]')
      .trigger("keydown", { key: "Enter" });
    await flushPromises();
    const remove = Array.from(
      document.querySelectorAll('[role="menuitem"]'),
    ).find((item) => item.textContent?.includes("agent.workspace.delete"));
    (remove as HTMLElement).click();
    await flushPromises();
    const dialog = document.querySelector('[role="alertdialog"]');
    expect(dialog?.textContent).toContain("agent.workspace.deleteConfirm");
    expect(state.deleteCurrentConversation).not.toHaveBeenCalled();
    const keep = Array.from(dialog!.querySelectorAll("button")).find(
      (button) => button.textContent === "agent.workspace.keepConversation",
    );
    keep!.click();
    await flushPromises();
    expect(state.deleteCurrentConversation).not.toHaveBeenCalled();
    state.turns.value[0].answer = null;
    state.turns.value[0].status = "queued";
    await flushPromises();
    const cancel = wrapper
      .findAll("button")
      .find((button) => button.text() === "agent.workspace.cancelTurn");
    expect(cancel).toBeDefined();
    await cancel!.trigger("click");
    expect(state.cancelTurn).toHaveBeenCalledWith(state.turns.value[0]);
    state.turns.value[0].status = "failed";
    state.turns.value[0].failureReason = "ModelProtocolError";
    await flushPromises();
    expect(wrapper.text()).toContain("agent.workspace.modelResponseInvalid");
    for (const reason of [
      "model_answer_json_invalid",
      "model_answer_bounds_invalid",
      "model_citation_invalid",
    ]) {
      state.turns.value[0].failureReason = reason;
      await flushPromises();
      expect(wrapper.text()).toContain("agent.workspace.modelResponseInvalid");
    }
    for (const reason of [
      "answer_source_diagnosis_unverified",
      "citation_claim_unverified",
    ]) {
      state.turns.value[0].failureReason = reason;
      await flushPromises();
      expect(wrapper.text()).toContain("agent.workspace.evidenceNotVerified");
      expect(wrapper.text()).not.toContain("agent.workspace.turnFailed");
    }
    state.turns.value[0].failureReason = "answer_source_reference_unverified";
    state.turns.value.push({
      ...state.turns.value[0],
      turnId: "turn-2",
      turnIndex: 2,
      status: "completed",
      answer: "Verified result",
      failureReason: null,
    });
    await flushPromises();
    const earlier = wrapper
      .findAll("button")
      .find((button) =>
        button.text().includes("agent.workspace.earlierAnalysisIncomplete"),
      );
    expect(earlier?.attributes("aria-expanded")).toBe("false");
    expect(wrapper.text()).not.toContain(
      "agent.workspace.sourceResponseBlocked",
    );
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    await earlier!.trigger("click");
    await flushPromises();
    expect(wrapper.text()).toContain("agent.workspace.sourceResponseBlocked");
    wrapper.unmount();
  });

  it("shows the administrator-disabled state from capabilities", async () => {
    mocks.getCapabilities.mockResolvedValue({
      featureEnabled: false,
      analysisAvailable: false,
      persistenceAvailable: false,
      conversationHistoryAvailable: false,
      sourceMode: "metadata",
    });
    const wrapper = mount(AgentConversationWorkspace, {
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    await flushPromises();
    expect(mocks.getCapabilities).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain("agent.workspace.unavailable");
    wrapper.unmount();
  });
});
