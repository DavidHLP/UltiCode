import { effectScope } from "vue";
import { flushPromises } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  createConversation: vi.fn(),
  listConversations: vi.fn(),
  getConversation: vi.fn(),
  createConversationTurn: vi.fn(),
  runConversationTurn: vi.fn(),
  cancelConversationTurn: vi.fn(),
  recoverConversationTurn: vi.fn(),
  deleteConversation: vi.fn(),
  conversationEventStreamUrl: vi.fn(() => "/api/events"),
}));

vi.mock("@/api/agent", () => ({ agentApi: api }));

import { useAgentConversations } from "../useAgentConversations";

const conversation = {
  conversationId: "conversation-1",
  sourceSubmissionId: "submission-1",
  title: "Initial question",
  turnCount: 1,
  lastQuestion: "Initial question",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const firstTurn = {
  turnId: "turn-1",
  conversationId: conversation.conversationId,
  turnIndex: 1,
  runId: "run-1",
  status: "queued" as const,
  question: "Initial question",
  answer: null,
  citations: [],
  trace: [],
  failureReason: null,
  cancelRequested: false,
  eventSeq: 0,
  createdAt: conversation.createdAt,
  updatedAt: conversation.updatedAt,
};

class TestEventSource {
  onerror: ((event: Event) => void) | null = null;
  addEventListener() {}
  close() {}
}

describe("useAgentConversations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("EventSource", TestEventSource);
    api.listConversations.mockResolvedValue({ conversations: [conversation], nextOffset: null });
    api.deleteConversation.mockResolvedValue({ conversationId: conversation.conversationId, deleted: true });
  });

  it("creates first and follow-up turns with per-turn consent", async () => {
    api.createConversation.mockResolvedValue({ conversation, turn: firstTurn });
    api.runConversationTurn.mockResolvedValue({ ...firstTurn, status: "completed", answer: "First answer" });
    api.createConversationTurn.mockResolvedValue({
      ...firstTurn,
      turnId: "turn-2",
      turnIndex: 2,
      runId: "run-2",
      question: "Follow-up question",
    });
    api.runConversationTurn.mockResolvedValueOnce({ ...firstTurn, status: "completed", answer: "First answer" })
      .mockResolvedValueOnce({ ...firstTurn, turnId: "turn-2", turnIndex: 2, runId: "run-2", question: "Follow-up question", status: "completed", answer: "Second answer" });
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    workspace.startNewConversation("submission-1");

    await workspace.sendTurn("Initial question", true);
    await workspace.sendTurn("Follow-up question", false);

    expect(api.createConversation).toHaveBeenCalledWith(expect.objectContaining({
      sourceSubmissionId: "submission-1",
      question: "Initial question",
    }));
    expect(api.runConversationTurn).toHaveBeenNthCalledWith(1, "conversation-1", "turn-1", true);
    expect(api.createConversationTurn).toHaveBeenCalledWith(
      "conversation-1", expect.any(String), "Follow-up question",
    );
    expect(api.runConversationTurn).toHaveBeenNthCalledWith(2, "conversation-1", "turn-2", false);
    expect(workspace.turns.value.map((turn) => turn.answer)).toEqual(["First answer", "Second answer"]);
    scope.stop();
  });

  it("cancels only active turn and blocks deletion until run settles", async () => {
    api.createConversation.mockResolvedValue({ conversation, turn: firstTurn });
    let resolveRun!: (value: typeof firstTurn) => void;
    api.runConversationTurn.mockReturnValue(new Promise((resolve) => { resolveRun = resolve; }));
    api.cancelConversationTurn.mockResolvedValue({ ...firstTurn, status: "cancelled" });
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    workspace.startNewConversation("submission-1");
    const sending = workspace.sendTurn("Initial question", false);
    await flushPromises();

    expect(workspace.currentTurn.value?.status).toBe("running");
    await workspace.cancelTurn();
    expect(api.cancelConversationTurn).toHaveBeenCalledWith("conversation-1", "turn-1");
    expect(workspace.turns.value[0].status).toBe("cancelled");
    expect(await workspace.deleteCurrentConversation()).toBe(false);
    expect(api.deleteConversation).not.toHaveBeenCalled();

    resolveRun({ ...firstTurn, status: "cancelled" });
    await sending;
    expect(await workspace.deleteCurrentConversation()).toBe(true);
    expect(workspace.conversation.value).toBeNull();
    expect(api.deleteConversation).toHaveBeenCalledWith("conversation-1");
    scope.stop();
  });
});
