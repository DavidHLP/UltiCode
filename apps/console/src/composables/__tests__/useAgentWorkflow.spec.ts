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
