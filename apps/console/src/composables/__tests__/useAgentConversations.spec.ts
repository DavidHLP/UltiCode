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
import { ApiError } from "@/utils/request";

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

  emit(type: string, payload: Record<string, unknown>) {
    this.listeners.get(type)?.(
      new MessageEvent(type, { data: JSON.stringify(payload) }),
    );
  }

  close() {
    this.closed = true;
  }
}

describe("useAgentConversations", () => {
  it("clears a confirmed stale POST warning when the newer observed run completes", async () => {
    const failed = {
      ...firstTurn,
      status: "failed",
      failureReason: "citation_claim_unverified",
    };
    const current = {
      ...failed,
      runId: "new-run",
      status: "running",
      failureReason: null,
    };
    const completed = { ...current, status: "completed", answer: "Verified" };
    let rejectRun!: (error: Error) => void;
    api.getConversation.mockResolvedValue({ conversation, turns: [failed], hasMore: false });
    api.runConversationTurn.mockReturnValue(new Promise((_, reject) => { rejectRun = reject; }));
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    const sending = workspace.retryTurn(failed, true);
    api.getConversation.mockResolvedValue({ conversation, turns: [current], hasMore: false });
    TestEventSource.instances[0].emit("stream_closed", { reason: "run_replaced" });
    await flushPromises();
    rejectRun(new ApiError("run_replaced", 409, {
      status: 409,
      data: { code: 40900, message: "run_replaced", data: { reason: "run_replaced" } },
    }));
    await sending;
    expect(workspace.error.value).toBe("runReplaced");
    expect(workspace.busy.value).toBe(true);
    expect(workspace.currentTurn.value?.runId).toBe(current.runId);
    expect(workspace.currentTurn.value?.status).toBe("running");

    api.getConversation.mockResolvedValue({ conversation, turns: [completed], hasMore: false });
    TestEventSource.instances.at(-1)!.emit("terminal", { runId: current.runId });
    await flushPromises();
    expect(workspace.error.value).toBe("");
    expect(workspace.busy.value).toBe(false);
    expect(workspace.turns.value[0]).toEqual(completed);
    expect(api.runConversationTurn).toHaveBeenCalledTimes(1);
    scope.stop();
  });

  it.each([
    { status: 409, reason: "turn_active" },
    { status: 502, reason: "run_replaced" },
  ])("preserves uncertain POST errors for $status $reason", async ({ status, reason }) => {
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [firstTurn],
      hasMore: false,
    });
    api.runConversationTurn.mockRejectedValue(new ApiError(reason, status, {
      status,
      data: { data: { reason } },
    }));
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    await workspace.retryTurn(firstTurn, false);
    expect(workspace.error.value).toBe("requestFailed");
    expect(workspace.busy.value).toBe(true);
    expect(api.runConversationTurn).toHaveBeenCalledTimes(1);
    scope.stop();
  });

  it("does not reopen an old stream when rejected POST recovery finishes after navigation", async () => {
    let resolveRecovery!: (page: unknown) => void;
    api.getConversation
      .mockResolvedValueOnce({ conversation, turns: [firstTurn], hasMore: false })
      .mockReturnValueOnce(new Promise((resolve) => { resolveRecovery = resolve; }));
    api.runConversationTurn.mockRejectedValue(new Error("connection lost"));
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    const sending = workspace.retryTurn(firstTurn, false);
    await flushPromises();
    expect(api.getConversation).toHaveBeenCalledTimes(2);

    const nextConversation = { ...conversation, conversationId: "conversation-2", sourceSubmissionId: "submission-2" };
    const nextTurn = { ...firstTurn, conversationId: nextConversation.conversationId, turnId: "turn-2", status: "completed", answer: "Current answer" };
    api.getConversation.mockResolvedValueOnce({ conversation: nextConversation, turns: [nextTurn], hasMore: false });
    await workspace.openConversation(nextConversation.conversationId);
    expect(TestEventSource.instances[0].closed).toBe(true);

    resolveRecovery({ conversation, turns: [{ ...firstTurn, status: "running" }], hasMore: false });
    await sending;
    expect(TestEventSource.instances).toHaveLength(1);
    expect(workspace.conversation.value).toEqual(nextConversation);
    expect(workspace.sourceSubmissionId.value).toBe("submission-2");
    expect(workspace.turns.value).toEqual([nextTurn]);
    expect(workspace.events.value).toEqual([]);
    expect(workspace.busy.value).toBe(false);
    expect(workspace.currentTurn.value).toBeNull();
    expect(workspace.error.value).toBe("");
    expect(api.runConversationTurn).toHaveBeenCalledTimes(1);
    scope.stop();
  });

  it("clears replacement warnings when the current run finishes through SSE before its POST settles", async () => {
    const failed = {
      ...firstTurn,
      status: "failed",
      failureReason: "citation_claim_unverified",
    };
    const current = {
      ...failed,
      runId: "new-run",
      status: "running",
      failureReason: null,
    };
    const completed = { ...current, status: "completed", answer: "Verified" };
    let complete!: (value: unknown) => void;
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [failed],
      hasMore: false,
    });
    api.runConversationTurn.mockReturnValue(
      new Promise((resolve) => {
        complete = resolve;
      }),
    );
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    const running = workspace.retryTurn(failed, true);
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [current],
      hasMore: false,
    });
    TestEventSource.instances[0].emit("stream_closed", {
      reason: "run_replaced",
    });
    await flushPromises();
    expect(workspace.error.value).toBe("runReplaced");
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [completed],
      hasMore: false,
    });
    TestEventSource.instances
      .at(-1)!
      .emit("terminal", { runId: current.runId });
    await flushPromises();
    expect(workspace.busy.value).toBe(false);
    expect(workspace.error.value).toBe("");
    complete(completed);
    await running;
    scope.stop();
  });

  it("keeps a retry busy when the previous run's terminal event arrives before its POST completes", async () => {
    const failed = {
      ...firstTurn,
      status: "failed",
      failureReason: "citation_claim_unverified",
    };
    let complete!: (value: unknown) => void;
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [failed],
      hasMore: false,
    });
    api.runConversationTurn.mockReturnValue(
      new Promise((resolve) => {
        complete = resolve;
      }),
    );
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    const running = workspace.retryTurn(failed, true);
    TestEventSource.instances[0].emit("terminal", { runId: firstTurn.runId });
    await flushPromises();
    expect(workspace.busy.value).toBe(true);
    complete({
      ...failed,
      runId: "new-run",
      status: "completed",
      failureReason: null,
      answer: "Verified",
    });
    await running;
    expect(workspace.busy.value).toBe(false);
    expect(workspace.turns.value[0].runId).toBe("new-run");
    scope.stop();
  });

  it.each(["runReplaced", "streamFailed", "streamTimedOut"])(
    "clears recovered %s after the run response confirms completion",
    async (message) => {
      let complete!: (value: unknown) => void;
      api.getConversation.mockResolvedValue({
        conversation,
        turns: [firstTurn],
        hasMore: false,
      });
      api.runConversationTurn.mockReturnValue(
        new Promise((resolve) => {
          complete = resolve;
        }),
      );
      const scope = effectScope();
      const workspace = scope.run(() => useAgentConversations())!;
      await workspace.openConversation(conversation.conversationId);
      const running = workspace.retryTurn(firstTurn, false);
      const stream = TestEventSource.instances[0];
      if (message === "streamFailed") stream.onerror?.(new Event("error"));
      else
        stream.emit("stream_closed", {
          reason: message === "runReplaced" ? "run_replaced" : "stream_timeout",
        });
      await flushPromises();
      expect(workspace.error.value).toBe(message);
      complete({
        ...firstTurn,
        status: "completed",
        answer: "Verified answer",
      });
      await running;
      expect(
        workspace.turns.value.find((turn) => turn.turnId === firstTurn.turnId)
          ?.status,
      ).toBe("completed");
      expect(workspace.error.value).toBe("");
      expect(workspace.busy.value).toBe(false);
      scope.stop();
    },
  );

  it("clears a deleted conversation without refreshing or reconnecting its stream", async () => {
    const running = { ...firstTurn, status: "running" };
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [running],
      hasMore: false,
    });
    api.recoverConversationTurn.mockResolvedValue(running);
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    const stream = TestEventSource.instances[0];
    stream.listeners.get("stream_closed")?.(
      new MessageEvent("stream_closed", {
        data: JSON.stringify({ reason: "conversation_deleted" }),
      }),
    );
    stream.onerror?.(new Event("error"));
    await flushPromises();
    expect(stream.closed).toBe(true);
    expect(TestEventSource.instances).toHaveLength(1);
    expect(api.getConversation).toHaveBeenCalledTimes(1);
    expect(workspace.conversation.value).toBeNull();
    expect(workspace.currentTurn.value).toBeNull();
    expect(workspace.busy.value).toBe(false);
    expect(workspace.turns.value).toEqual([]);
    expect(workspace.sourceSubmissionId.value).toBe("");
    expect(workspace.error.value).toBe("");
    scope.stop();
  });

  it("allows cancelling a selected legacy queued turn before retrying another", async () => {
    const second = { ...firstTurn, turnId: "turn-2", turnIndex: 2 };
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [firstTurn, second],
      hasMore: false,
    });
    api.cancelConversationTurn.mockResolvedValue({
      ...second,
      status: "cancelled",
    });
    api.runConversationTurn.mockResolvedValue({
      ...firstTurn,
      status: "completed",
    });
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    await workspace.cancelTurn(second);
    expect(api.cancelConversationTurn).toHaveBeenCalledWith(
      conversation.conversationId,
      second.turnId,
    );
    expect(workspace.turns.value[1].status).toBe("cancelled");
    await workspace.retryTurn(firstTurn, false);
    expect(api.runConversationTurn).toHaveBeenCalledWith(
      conversation.conversationId,
      firstTurn.turnId,
      false,
      firstTurn.runId,
    );
    scope.stop();
  });

  it("keeps a different running turn active when cancelling a queued sibling", async () => {
    const running = { ...firstTurn, status: "running" };
    const queued = { ...firstTurn, turnId: "turn-2", turnIndex: 2 };
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [running, queued],
      hasMore: false,
    });
    api.recoverConversationTurn.mockResolvedValue(running);
    api.cancelConversationTurn.mockResolvedValue({
      ...queued,
      status: "cancelled",
    });
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    await workspace.cancelTurn(queued);
    expect(workspace.busy.value).toBe(true);
    expect(workspace.currentTurn.value?.turnId).toBe(running.turnId);
    expect(TestEventSource.instances[0].closed).toBe(false);
    scope.stop();
  });

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

  it("shows a confirmed failed turn without misreporting a connection failure or rerunning it", async () => {
    api.getConversation
      .mockResolvedValueOnce({ conversation, turns: [], hasMore: false })
      .mockResolvedValueOnce({
        conversation,
        turns: [
          {
            ...firstTurn,
            status: "failed",
            failureReason: "ModelProtocolError",
          },
        ],
        hasMore: false,
      });
    api.runConversationTurn.mockRejectedValue(new Error("upstream rejected"));
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    await workspace.retryTurn(firstTurn, false);
    expect(workspace.error.value).toBe("");
    expect(workspace.turns.value[0].failureReason).toBe("ModelProtocolError");
    expect(workspace.busy.value).toBe(false);
    expect(api.runConversationTurn).toHaveBeenCalledTimes(1);
    scope.stop();
  });

  it("sorts Unix seconds together with ISO conversation dates", async () => {
    const recent = {
      ...conversation,
      conversationId: "recent",
      updatedAt: 1792750312.355659,
    };
    api.listConversations.mockResolvedValue({
      conversations: [recent],
      nextOffset: null,
    });
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [],
      hasMore: false,
    });
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.loadConversations(true);
    await workspace.openConversation(conversation.conversationId);
    expect(
      workspace.conversations.value.map((item) => item.conversationId),
    ).toEqual(["recent", conversation.conversationId]);
    expect(workspace.error.value).toBe("");
    scope.stop();
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

  it.each(["errors", "deadline"])(
    "releases the active turn after cancellation when observation stopped at %s",
    async (cutoff) => {
      vi.useFakeTimers();
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
      const stream = TestEventSource.instances[0];

      if (cutoff === "deadline") {
        await vi.advanceTimersByTimeAsync(2 * 60 * 1000);
      } else {
        for (let attempt = 0; attempt < 5; attempt += 1) {
          stream.onerror?.(new Event("error"));
          await flushPromises();
        }
      }

      expect(stream.closed).toBe(true);
      expect(workspace.busy.value).toBe(true);
      expect(workspace.currentTurn.value?.turnId).toBe(firstTurn.turnId);
      expect(api.runConversationTurn).not.toHaveBeenCalled();
      api.cancelConversationTurn.mockResolvedValue({
        ...firstTurn,
        status: "cancelled",
      });
      await workspace.cancelTurn();
      expect(workspace.busy.value).toBe(false);
      expect(workspace.currentTurn.value).toBeNull();
      expect(TestEventSource.instances).toHaveLength(1);
      expect(await workspace.deleteCurrentConversation()).toBe(true);
      scope.stop();
      vi.useRealTimers();
    },
  );

  it("closes a persistently observed stream at the total observation deadline", async () => {
    vi.useFakeTimers();
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
    const stream = TestEventSource.instances[0];

    await vi.advanceTimersByTimeAsync(2 * 60 * 1000);

    expect(stream.closed).toBe(true);
    expect(workspace.busy.value).toBe(true);
    expect(workspace.currentTurn.value?.turnId).toBe(firstTurn.turnId);
    expect(api.runConversationTurn).not.toHaveBeenCalled();
    scope.stop();
    vi.useRealTimers();
  });

  it("closes the current reconnected stream at the original observation deadline", async () => {
    vi.useFakeTimers();
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
    const reconnected = TestEventSource.instances[1];

    await vi.advanceTimersByTimeAsync(2 * 60 * 1000);

    expect(initial.closed).toBe(true);
    expect(reconnected.closed).toBe(true);
    expect(workspace.busy.value).toBe(true);
    expect(workspace.currentTurn.value?.turnId).toBe(firstTurn.turnId);
    expect(api.runConversationTurn).not.toHaveBeenCalled();
    scope.stop();
    vi.useRealTimers();
  });

  it("does not reopen a stream when delayed status recovery crosses the deadline", async () => {
    vi.useFakeTimers();
    let resolveStatus!: (page: {
      conversation: typeof conversation;
      turns: Array<Omit<typeof firstTurn, "status"> & { status: string }>;
      hasMore: boolean;
    }) => void;
    api.getConversation
      .mockResolvedValueOnce({
        conversation,
        turns: [{ ...firstTurn, status: "running" }],
        hasMore: false,
      })
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveStatus = resolve;
        }),
      );
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
    expect(TestEventSource.instances).toHaveLength(1);

    await vi.advanceTimersByTimeAsync(2 * 60 * 1000);
    resolveStatus({
      conversation,
      turns: [{ ...firstTurn, status: "running" }],
      hasMore: false,
    });
    await flushPromises();

    expect(TestEventSource.instances).toHaveLength(1);
    expect(workspace.busy.value).toBe(true);
    expect(workspace.currentTurn.value?.turnId).toBe(firstTurn.turnId);
    expect(api.runConversationTurn).not.toHaveBeenCalled();
    scope.stop();
    vi.useRealTimers();
  });

  it("keeps an unrecovered running turn busy and allows explicit retry of queued turns", async () => {
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [{ ...firstTurn, status: "running" }],
      hasMore: false,
    });
    api.recoverConversationTurn.mockRejectedValue(new Error("offline"));
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;

    await workspace.openConversation(conversation.conversationId);

    expect(workspace.error.value).toBe("requestFailed");
    expect(workspace.busy.value).toBe(true);
    expect(workspace.currentTurn.value?.turnId).toBe(firstTurn.turnId);
    expect(workspace.startNewConversation("submission-1")).toBe(false);

    scope.stop();
  });

  it("allows a queued turn to be explicitly retried after loading", async () => {
    api.getConversation.mockResolvedValue({
      conversation,
      turns: [{ ...firstTurn, status: "queued" }],
      hasMore: false,
    });
    api.runConversationTurn.mockResolvedValue({
      ...firstTurn,
      status: "queued",
    });
    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;

    await workspace.openConversation(conversation.conversationId);
    expect(workspace.busy.value).toBe(false);
    await workspace.retryTurn({ ...firstTurn, status: "queued" }, false);

    expect(api.runConversationTurn).toHaveBeenCalledWith(
      conversation.conversationId,
      firstTurn.turnId,
      false,
      firstTurn.runId,
    );
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

  it.each(["resolved", "rejected"])(
    "ignores a late %s POST when the same turn has already been retried again",
    async (outcome) => {
      const failed = { ...firstTurn, status: "failed", failureReason: "citation_claim_unverified" };
      const retried = { ...failed, runId: "run-2" };
      const current = { ...retried, runId: "run-3", status: "running", failureReason: null };
      api.getConversation.mockResolvedValue({ conversation, turns: [failed], hasMore: false });
      let settle!: (value: unknown) => void;
      let reject!: (reason: Error) => void;
      api.runConversationTurn.mockReturnValueOnce(new Promise((resolve, fail) => { settle = resolve; reject = fail; }));
      const scope = effectScope();
      const workspace = scope.run(() => useAgentConversations())!;
      await workspace.openConversation(conversation.conversationId);
      const oldRequest = workspace.retryTurn(failed, false);
      await flushPromises();
      api.getConversation.mockResolvedValue({ conversation, turns: [retried], hasMore: false });
      TestEventSource.instances[0].emit("stream_closed", { reason: "run_replaced" });
      await flushPromises();
      expect(workspace.busy.value).toBe(false);
      let finish!: (value: unknown) => void;
      api.runConversationTurn.mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
      const newRequest = workspace.retryTurn(retried, false);
      await flushPromises();
      api.getConversation.mockResolvedValue({ conversation, turns: [current], hasMore: false });
      TestEventSource.instances[1].emit("stream_closed", { reason: "run_replaced" });
      await flushPromises();
      if (outcome === "resolved") settle(retried);
      else reject(new Error("old connection lost"));
      await oldRequest;
      expect(workspace.busy.value).toBe(true);
      expect(workspace.currentTurn.value?.runId).toBe("run-3");
      expect(workspace.currentTurn.value?.status).toBe("running");
      expect(TestEventSource.instances[2].closed).toBe(false);
      finish({ ...current, status: "completed", answer: "Verified" });
      await newRequest;
      scope.stop();
    },
  );

  it.each(["resolved", "rejected"])(
    "does not let an old %s POST settlement clear a later turn after a terminal event",
    async (outcome) => {
      api.createConversation.mockResolvedValue({
        conversation,
        turn: firstTurn,
      });
      let rejectRun!: (error: Error) => void;
      let resolveRun!: (value: unknown) => void;
      api.runConversationTurn.mockReturnValueOnce(
        new Promise((resolve, reject) => {
          resolveRun = resolve;
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
      TestEventSource.instances[1].onerror?.(new Event("error"));
      await flushPromises();
      expect(workspace.error.value).toBe("streamFailed");
      if (outcome === "rejected") rejectRun(new Error("late response lost"));
      else resolveRun({ ...firstTurn, status: "completed", answer: "Done" });
      await firstRequest;
      expect(workspace.busy.value).toBe(true);
      expect(workspace.currentTurn.value?.turnId).toBe(second.turnId);
      expect(workspace.error.value).toBe("streamFailed");
      expect(TestEventSource.instances[1].closed).toBe(false);
      resolveSecond({ ...second, status: "queued" });
      await secondRequest;
      scope.stop();
    },
  );

  it("refreshes and settles a recovered turn on terminal without a run id", async () => {
    const runningTurn = { ...firstTurn, status: "running" as const };
    const completedTurn = {
      ...firstTurn,
      status: "completed" as const,
      answer: "Done",
    };
    api.getConversation
      .mockResolvedValueOnce({
        conversation,
        turns: [runningTurn],
        hasMore: false,
      })
      .mockResolvedValueOnce({
        conversation,
        turns: [completedTurn],
        hasMore: false,
      });
    api.recoverConversationTurn.mockResolvedValue({
      ...firstTurn,
      status: "running",
    });

    const scope = effectScope();
    const workspace = scope.run(() => useAgentConversations())!;
    await workspace.openConversation(conversation.conversationId);
    const stream = TestEventSource.instances[0];
    stream.emit("terminal", {
      status: "completed",
      seq: 3,
      reason: "completed",
      cancelRequested: false,
    });

    await vi.waitFor(() => {
      expect(workspace.turns.value[0]?.status).toBe("completed");
      expect(workspace.busy.value).toBe(false);
    });
    expect(stream.closed).toBe(true);
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
      "run-1",
    );
    expect(api.createConversationTurn).toHaveBeenCalledWith(
      "conversation-1",
      expect.any(String),
      "Follow-up question",
      undefined,
    );
    expect(api.runConversationTurn).toHaveBeenNthCalledWith(
      2,
      "conversation-1",
      "turn-2",
      false,
      "run-2",
    );
    expect(workspace.turns.value.map((turn) => turn.answer)).toEqual([
      "First answer",
      "Second answer",
    ]);
    scope.stop();
  });

  it("releases a cancelled active turn before the run POST settles", async () => {
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
    expect(workspace.busy.value).toBe(false);
    expect(workspace.currentTurn.value).toBeNull();
    expect(TestEventSource.instances[0].closed).toBe(true);

    resolveRun({ ...firstTurn, status: "cancelled" });
    await sending;
    expect(await workspace.deleteCurrentConversation()).toBe(true);
    expect(workspace.conversation.value).toBeNull();
    expect(api.deleteConversation).toHaveBeenCalledWith("conversation-1");
    scope.stop();
  });
});
