import { effectScope } from "vue";
import { afterEach, describe, expect, it, vi } from "vitest";
import { agentApi, type AgentThread } from "@/api/agent";
import { useAgentWorkflow } from "@/composables/useAgentWorkflow";

vi.mock("@/api/agent", () => ({
  agentApi: {
    getThread: vi.fn(),
    createThread: vi.fn(),
    analyze: vi.fn(),
    updateDraft: vi.fn(),
    confirm: vi.fn(),
    save: vi.fn(),
    recover: vi.fn(),
    cancel: vi.fn(),
    eventStreamUrl: vi.fn((id: string) => `/api/agent/threads/${id}/events/stream?after=0`),
  },
}));

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  private listeners = new Map<string, (event: MessageEvent<string>) => void>();
  onerror: ((event: Event) => unknown) | null = null;
  closed = false;

  constructor(public url: string) {
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: EventListener) {
    this.listeners.set(type, listener as (event: MessageEvent<string>) => void);
  }

  close() {
    this.closed = true;
  }

  emit(type: string, payload: Record<string, unknown>) {
    this.listeners.get(type)?.(new MessageEvent(type, { data: JSON.stringify(payload) }));
  }

  fail() {
    this.onerror?.(new Event("error"));
  }
}

function thread(threadId: string, runId: string, status: AgentThread["status"]): AgentThread {
  return {
    threadId,
    runId,
    status,
    sourceSubmissionId: "submission-1",
    question: "review this",
    draft: {
      draftVersion: 1,
      title: "Draft",
      content: "Content",
      facts: [],
      hypotheses: [],
      citations: [],
      citationChecks: [],
      sampleScope: null,
    },
    paramsDigest: "digest",
    analysis: null,
    confirmation: null,
    receipt: { planId: null, cancelRequested: false, failureReason: null },
  };
}

describe("useAgentWorkflow run isolation", () => {
  afterEach(() => {
    vi.clearAllMocks();
    vi.unstubAllGlobals();
    FakeEventSource.instances = [];
  });

  it("ignores late events from a cancelled thread after a new thread starts", async () => {
    vi.stubGlobal("EventSource", FakeEventSource);
    const first = thread("thread-a", "run-a", "analyzing");
    const cancelled = { ...first, status: "cancelled" as const };
    const second = thread("thread-b", "run-b", "awaiting_confirmation");
    vi.mocked(agentApi.getThread).mockResolvedValueOnce(first);
    vi.mocked(agentApi.cancel).mockResolvedValue(cancelled);
    vi.mocked(agentApi.createThread).mockResolvedValue(second);

    const scope = effectScope();
    const workflow = scope.run(() => useAgentWorkflow())!;
    await workflow.loadThread(first.threadId);
    const oldStream = FakeEventSource.instances[0];
    expect(oldStream.url).toContain("thread-a");

    await workflow.cancel();
    expect(oldStream.closed).toBe(true);
    await workflow.createThread("submission-2", "new question");
    oldStream.emit("text", { runId: first.runId, text: "stale answer" });

    expect(workflow.thread.value?.threadId).toBe(second.threadId);
    expect(workflow.generatedText.value).toBe("");
    scope.stop();
  });

  it("reloads and subscribes to the canonical run when the server replaces a run", async () => {
    vi.stubGlobal("EventSource", FakeEventSource);
    const previous = thread("thread-a", "run-a", "analyzing");
    const current = thread("thread-a", "run-b", "analyzing");
    vi.mocked(agentApi.getThread).mockResolvedValueOnce(previous).mockResolvedValueOnce(current);

    const scope = effectScope();
    const workflow = scope.run(() => useAgentWorkflow())!;
    await workflow.loadThread(previous.threadId);
    FakeEventSource.instances[0].emit("stream_closed", {
      runId: previous.runId,
      reason: "run_replaced",
    });

    await vi.waitFor(() => expect(workflow.thread.value?.runId).toBe(current.runId));
    expect(FakeEventSource.instances).toHaveLength(2);
    expect(FakeEventSource.instances[1].closed).toBe(false);
    scope.stop();
  });

  it("submits only one confirmation and keeps confirmation separate from saving", async () => {
    const awaiting = thread("thread-a", "run-a", "awaiting_confirmation");
    const confirmed = {
      ...awaiting,
      status: "confirmed" as const,
      confirmation: { id: "confirmation-1" },
    };
    let resolveConfirm!: (value: AgentThread) => void;
    const confirmRequest = new Promise<AgentThread>((resolve) => {
      resolveConfirm = resolve;
    });
    vi.mocked(agentApi.getThread).mockResolvedValue(awaiting);
    vi.mocked(agentApi.confirm).mockReturnValue(confirmRequest);

    const scope = effectScope();
    const workflow = scope.run(() => useAgentWorkflow())!;
    await workflow.loadThread(awaiting.threadId);

    const firstConfirm = workflow.confirmDraft();
    const duplicateConfirm = workflow.confirmDraft();
    expect(agentApi.confirm).toHaveBeenCalledTimes(1);
    expect(workflow.busy.value).toBe(true);

    resolveConfirm(confirmed);
    await Promise.all([firstConfirm, duplicateConfirm]);
    expect(workflow.thread.value?.status).toBe("confirmed");
    expect(agentApi.save).not.toHaveBeenCalled();
    scope.stop();
  });

  it("shows the canonical confirmation after a lost response and never saves automatically", async () => {
    const awaiting = thread("thread-a", "run-a", "awaiting_confirmation");
    const confirmed = {
      ...awaiting,
      status: "confirmed" as const,
      confirmation: { id: "confirmation-1" },
    };
    vi.mocked(agentApi.getThread).mockResolvedValueOnce(awaiting).mockResolvedValueOnce(confirmed);
    vi.mocked(agentApi.confirm).mockRejectedValue(new Error("response lost"));

    const scope = effectScope();
    const workflow = scope.run(() => useAgentWorkflow())!;
    await workflow.loadThread(awaiting.threadId);
    await workflow.confirmDraft();

    expect(workflow.error.value).toBe("requestFailed");
    expect(workflow.thread.value?.status).toBe("confirmed");
    expect(agentApi.save).not.toHaveBeenCalled();
    scope.stop();
  });

  it("shows the canonical cancellation after a lost response", async () => {
    vi.stubGlobal("EventSource", FakeEventSource);
    const analyzing = thread("thread-a", "run-a", "analyzing");
    const cancelled = thread("thread-a", "run-a", "cancelled");
    vi.mocked(agentApi.getThread).mockResolvedValueOnce(analyzing).mockResolvedValueOnce(cancelled);
    vi.mocked(agentApi.cancel).mockRejectedValue(new Error("response lost"));

    const scope = effectScope();
    const workflow = scope.run(() => useAgentWorkflow())!;
    await workflow.loadThread(analyzing.threadId);
    await workflow.cancel();

    expect(workflow.error.value).toBe("requestFailed");
    expect(workflow.thread.value?.status).toBe("cancelled");
    expect(FakeEventSource.instances[0].closed).toBe(true);
    scope.stop();
  });

  it.each([
    { reason: "disconnect", expectedError: "streamFailed" },
    { reason: "stream_timeout", expectedError: "streamTimedOut" },
  ])("shows the stream $reason and restores canonical status", async ({ reason, expectedError }) => {
    vi.stubGlobal("EventSource", FakeEventSource);
    const analyzing = thread("thread-a", "run-a", "analyzing");
    const failed = thread("thread-a", "run-a", "failed");
    vi.mocked(agentApi.getThread).mockResolvedValueOnce(analyzing).mockResolvedValueOnce(failed);

    const scope = effectScope();
    const workflow = scope.run(() => useAgentWorkflow())!;
    await workflow.loadThread(analyzing.threadId);
    const stream = FakeEventSource.instances[0];
    if (reason === "disconnect") stream.fail();
    else stream.emit("stream_closed", { runId: analyzing.runId, reason });

    await vi.waitFor(() => {
      expect(workflow.error.value).toBe(expectedError);
      expect(workflow.thread.value?.status).toBe("failed");
    });
    expect(stream.closed).toBe(true);
    scope.stop();
  });

  it.each(["saved", "failed", "cancelled"] as const)(
    "restores the canonical %s status when a thread is reopened",
    async (status) => {
      const canonical = thread("thread-a", "run-a", status);
      vi.mocked(agentApi.getThread).mockResolvedValue(canonical);
      const scope = effectScope();
      const workflow = scope.run(() => useAgentWorkflow())!;

      await workflow.loadThread(canonical.threadId);

      expect(workflow.thread.value?.status).toBe(status);
      scope.stop();
    },
  );

  it("shows saving state and refuses cancellation while a save is in flight", async () => {
    const confirmed = {
      ...thread("thread-a", "run-a", "confirmed"),
      confirmation: { id: "confirmation-1" },
    };
    const saved = { ...confirmed, status: "saved" as const };
    let resolveSave!: (value: AgentThread) => void;
    const saveRequest = new Promise<AgentThread>((resolve) => {
      resolveSave = resolve;
    });
    vi.mocked(agentApi.getThread).mockResolvedValue(confirmed);
    vi.mocked(agentApi.save).mockReturnValue(saveRequest);

    const scope = effectScope();
    const workflow = scope.run(() => useAgentWorkflow())!;
    await workflow.loadThread(confirmed.threadId);

    const pendingSave = workflow.save();
    expect(workflow.saving.value).toBe(true);
    await workflow.save();
    expect(agentApi.save).toHaveBeenCalledTimes(1);
    await workflow.cancel();
    expect(agentApi.cancel).not.toHaveBeenCalled();
    expect(await workflow.loadThread("thread-b")).toBeNull();
    expect(agentApi.getThread).toHaveBeenCalledTimes(1);
    expect(workflow.clear()).toBe(false);
    expect(workflow.thread.value?.threadId).toBe(confirmed.threadId);

    resolveSave(saved);
    await pendingSave;
    expect(workflow.saving.value).toBe(false);
    expect(workflow.thread.value?.status).toBe("saved");
    scope.stop();
  });
});
