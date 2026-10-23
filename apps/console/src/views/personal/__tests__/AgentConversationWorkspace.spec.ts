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

  afterEach(() => vi.unstubAllEnvs());

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
          answer: "<script>alert(1)</script>\n\n**Safe conclusion**",
          citations: [],
          trace: [],
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
    state.turns.value[0].answer = null;
    state.turns.value[0].status = "queued";
    await flushPromises();
    const cancel = wrapper
      .findAll("button")
      .find((button) => button.text() === "agent.workspace.cancelTurn");
    expect(cancel).toBeDefined();
    await cancel!.trigger("click");
    expect(state.cancelTurn).toHaveBeenCalledWith(state.turns.value[0]);
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
