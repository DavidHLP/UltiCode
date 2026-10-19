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
  static instances: TestEventSource[] = [];
  onerror: ((event: Event) => void) | null = null;
  listeners = new Map<string, (event: Event) => void>();
  closed = false;

  constructor() {
    TestEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: Event) => void) {
    this.listeners.set(type, listener);
  }

  close() {
    this.closed = true;
  }
}

describe("useAgentConversations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    TestEventSource.instances = [];
    vi.stubGlobal("EventSource", TestEventSource);
    api.listConversations.mockResolvedValue({
      conversations: [conversation],
      nextOffset: null,
    });
    api.deleteConversation.mockResolvedValue({
      conversationId: conversation.conversationId,
      deleted: true,
    });
  });

  it.each(["queued", "running"] as const)(
    "keeps observing when POST rejects and refresh reports %s",
    async (status) => {
      api.getConversation
        .mockResolvedValueOnce({ conversation, turns: [], hasMore: false })
        .mockResolvedValueOnce({
          conversation,
          turns: [{ ...firstTurn, status }],
          hasMore: false,
        });
      api.runConversationTurn.mockRejectedValue(
        new Error("connection lost after request"),
      );
      const scope = effectScope();
      const workspace = scope.run(() => useAgentConversations())!;
      await workspace.openConversation(conversation.conversationId);
      const turn = { ...firstTurn, status: "queued" as const };

      await workspace.retryTurn(turn, false);

      expect(api.runConversationTurn).toHaveBeenCalledTimes(1);
      expect(workspace.busy.value).toBe(true);
      expect(workspace.currentTurn.value?.turnId).toBe(turn.turnId);
      expect(TestEventSource.instances).toHaveLength(1);
      expect(TestEventSource.instances[0].closed).toBe(false);
      scope.stop();
    },
  );

  it("keeps observing when POST rejects and status refresh also fails", async () => {
    api.getConversation
      .mockResolvedValueOnce({ conversation, turns: [], hasMore: false })
      .mockRejectedValueOnce(new Error("refresh unavailable"));
    api.runConversationTurn.mockRejectedValue(
      new Error("connection lost after request"),
    );
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);

    await workspace.retryTurn(firstTurn, false);

    expect(workspace.error.value).toBe("requestFailed");
    expect(workspace.busy.value).toBe(true);
    expect(workspace.currentTurn.value?.turnId).toBe(firstTurn.turnId);
    expect(TestEventSource.instances[0].closed).toBe(false);
    api.getConversation.mockRejectedValueOnce(new Error("still offline"));
    TestEventSource.instances[0].onerror?.(new Event("error"));
    await flushPromises();
    expect(TestEventSource.instances[0].closed).toBe(false);
    expect(workspace.busy.value).toBe(true);

    api.getConversation.mockResolvedValueOnce({
      conversation,
      turns: [{ ...firstTurn, status: "completed", answer: "Recovered" }],
      hasMore: false,
    });
    TestEventSource.instances[0].onerror?.(new Event("error"));
    await flushPromises();
    expect(workspace.busy.value).toBe(false);
    expect(workspace.currentTurn.value).toBeNull();
    expect(TestEventSource.instances[0].closed).toBe(true);
    expect(api.runConversationTurn).toHaveBeenCalledTimes(1);
    scope.stop();
  });

  it("cleans up after a rejected POST when refresh confirms the turn is terminal", async () => {
    api.getConversation
      .mockResolvedValueOnce({ conversation, turns: [], hasMore: false })
      .mockResolvedValueOnce({
        conversation,
        turns: [{ ...firstTurn, status: "failed" }],
        hasMore: false,
      });
    api.runConversationTurn.mockRejectedValue(
      new Error("request outcome recovered"),
    );
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);

    await workspace.retryTurn(firstTurn, false);

    expect(workspace.busy.value).toBe(false);
    expect(workspace.currentTurn.value).toBeNull();
    expect(TestEventSource.instances[0].closed).toBe(true);
    scope.stop();
  });

  it("reconnects a timed-out stream without resending analysis and ignores disposed streams", async () => {
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [{ ...firstTurn, status: "running" }],
      hasMore: false,
    });
    api.recoverConversationTurn.mockResolvedValue({
      ...firstTurn,
      status: "running",
    });
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    const initial = TestEventSource.instances[0];
    initial.listeners.get("stream_closed")?.(
      new MessageEvent("stream_closed", {
        data: JSON.stringify({ reason: "stream_timeout" }),
      }),
    );
    await flushPromises();
    expect(initial.closed).toBe(true);
    expect(TestEventSource.instances).toHaveLength(2);
    expect(workspace.busy.value).toBe(true);
    expect(api.runConversationTurn).not.toHaveBeenCalled();
    scope.stop();
    const reads = api.getConversation.mock.calls.length;
    TestEventSource.instances[1].onerror?.(new Event("error"));
    await flushPromises();
    expect(api.getConversation).toHaveBeenCalledTimes(reads);
    expect(TestEventSource.instances[1].closed).toBe(true);
  });

  it("does not let an old POST settlement clear a later turn after a terminal event", async () => {
    api.createConversation.mockResolvedValue({ conversation, turn: firstTurn });
    let rejectRun!: (error: Error) => void;
    api.runConversationTurn.mockReturnValueOnce(
      new Promise((_resolve, reject) => {
        rejectRun = reject;
      }),
    );
    const second = {
      ...firstTurn,
      turnId: "turn-2",
      runId: "run-2",
      turnIndex: 2,
    };
    api.createConversationTurn.mockResolvedValue(second);
    let resolveSecond!: (value: typeof second) => void;
    api.runConversationTurn.mockReturnValueOnce(
      new Promise((resolve) => {
        resolveSecond = resolve;
      }),
    );
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [
        { ...firstTurn, status: "completed", answer: "Done" },
        { ...second, status: "running" },
      ],
      hasMore: false,
    });
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    workspace.startNewConversation("submission-1");
    const firstRequest = workspace.sendTurn("Initial question", false);
    await flushPromises();
    TestEventSource.instances[0].listeners.get("terminal")?.(
      new MessageEvent("terminal", {
        data: JSON.stringify({ runId: firstTurn.runId }),
      }),
    );
    await flushPromises();
    expect(workspace.busy.value).toBe(false);
    const secondRequest = workspace.sendTurn("Follow-up", false);
    await flushPromises();
    rejectRun(new Error("late response lost"));
    await firstRequest;
    expect(workspace.busy.value).toBe(true);
    expect(workspace.currentTurn.value?.turnId).toBe(second.turnId);
    expect(TestEventSource.instances[1].closed).toBe(false);
    resolveSecond({ ...second, status: "queued" });
    await secondRequest;
    scope.stop();
  });

  it("creates first and follow-up turns with per-turn consent", async () => {
    api.createConversation.mockResolvedValue({ conversation, turn: firstTurn });
    api.runConversationTurn.mockResolvedValue({
      ...firstTurn,
      status: "completed",
      answer: "First answer",
    });
    api.createConversationTurn.mockResolvedValue({
      ...firstTurn,
      turnId: "turn-2",
      turnIndex: 2,
      runId: "run-2",
      question: "Follow-up question",
    });
    api.runConversationTurn
      .mockResolvedValueOnce({
        ...firstTurn,
        status: "completed",
        answer: "First answer",
      })
      .mockResolvedValueOnce({
        ...firstTurn,
        turnId: "turn-2",
        turnIndex: 2,
        runId: "run-2",
        question: "Follow-up question",
        status: "completed",
        answer: "Second answer",
      });
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    workspace.startNewConversation("submission-1");

    await workspace.sendTurn("Initial question", true);
    await workspace.sendTurn("Follow-up question", false);

    expect(api.createConversation).toHaveBeenCalledWith(
      expect.objectContaining({
        sourceSubmissionId: "submission-1",
        question: "Initial question",
      }),
    );
    expect(api.runConversationTurn).toHaveBeenNthCalledWith(
      1,
      "conversation-1",
      "turn-1",
      true,
    );
    expect(api.createConversationTurn).toHaveBeenCalledWith(
      "conversation-1",
      expect.any(String),
      "Follow-up question",
    );
    expect(api.runConversationTurn).toHaveBeenNthCalledWith(
      2,
      "conversation-1",
      "turn-2",
      false,
    );
    expect(workspace.turns.value.map((turn) => turn.answer)).toEqual([
      "First answer",
      "Second answer",
    ]);
    scope.stop();
  });

  it("cancels only active turn and blocks deletion until run settles", async () => {
    api.createConversation.mockResolvedValue({ conversation, turn: firstTurn });
    let resolveRun!: (value: typeof firstTurn) => void;
    api.runConversationTurn.mockReturnValue(
      new Promise((resolve) => {
        resolveRun = resolve;
      }),
    );
    api.cancelConversationTurn.mockResolvedValue({
      ...firstTurn,
      status: "cancelled",
    });
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    workspace.startNewConversation("submission-1");
    const sending = workspace.sendTurn("Initial question", false);
    await flushPromises();

    expect(workspace.currentTurn.value?.status).toBe("running");
    await workspace.cancelTurn();
    expect(api.cancelConversationTurn).toHaveBeenCalledWith(
      "conversation-1",
      "turn-1",
    );
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
