import { flushPromises, mount } from "@vue/test-utils";
import { computed, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  getCapabilities: vi.fn(),
  route: { query: {} as Record<string, string> },
  replace: vi.fn(),
  workspace: null as object | null,
}));

vi.mock("@/api/agent", () => ({
  agentApi: { getCapabilities: mocks.getCapabilities },
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
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "true");
    mocks.route.query = { submissionId };
    mocks.workspace = createWorkspace();
    mocks.getCapabilities.mockResolvedValue({
      analysisAvailable: true,
      persistenceAvailable: true,
      conversationHistoryAvailable: true,
      sourceMode: "owner_source",
    });
  });

  afterEach(() => vi.unstubAllEnvs());

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
    const cancel = wrapper.findAll("button").find((button) => button.text() === "agent.workspace.cancelTurn");
    expect(cancel).toBeDefined();
    await cancel!.trigger("click");
    expect(state.cancelTurn).toHaveBeenCalledWith(state.turns.value[0]);
    wrapper.unmount();
  });

  it("does not request Agent capabilities while the feature flag is off", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "false");
    const wrapper = mount(AgentConversationWorkspace, {
      global: { stubs: { RouterLink: { template: "<a><slot /></a>" } } },
    });
    await flushPromises();
    expect(mocks.getCapabilities).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain("agent.workspace.unavailable");
    wrapper.unmount();
  });
});
