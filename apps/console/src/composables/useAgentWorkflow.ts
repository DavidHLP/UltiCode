import { onScopeDispose, ref } from "vue";
import {
  agentApi,
  type AgentThread,
  type AgentWorkflowEvent,
} from "@/api/agent";

const wait = (ms: number) =>
  new Promise<void>((resolve) => setTimeout(resolve, ms));

export function useAgentWorkflow() {
  const thread = ref<AgentThread | null>(null);
  const events = ref<AgentWorkflowEvent[]>([]);
  const generatedText = ref("");
  const draftTitle = ref("");
  const draftContent = ref("");
  const error = ref("");
  const busy = ref(false);
  const loading = ref(false);
  const analyzing = ref(false);
  const canceling = ref(false);
  const saving = ref(false);
  let runGeneration = 0;
  let eventSource: EventSource | null = null;
  let streamedRunId: string | null = null;

  function invalidateRun() {
    runGeneration += 1;
    eventSource?.close();
    eventSource = null;
    streamedRunId = null;
    analyzing.value = false;
    busy.value = false;
    canceling.value = false;
    saving.value = false;
    loading.value = false;
    return runGeneration;
  }

  function reset() {
    thread.value = null;
    events.value = [];
    generatedText.value = "";
    draftTitle.value = "";
    draftContent.value = "";
    error.value = "";
  }

  function isCurrent(token: number, threadId: string, runId?: string) {
    return (
      token === runGeneration &&
      thread.value?.threadId === threadId &&
      (!runId || thread.value.runId === runId)
    );
  }

  function applyThread(value: AgentThread, preserveAnswer = false) {
    const changedThread = thread.value?.threadId !== value.threadId;
    const changedDraft =
      thread.value?.draft.draftVersion !== value.draft.draftVersion;
    thread.value = value;
    draftTitle.value = value.draft.title;
    draftContent.value = value.draft.content;
    if (typeof value.analysis?.answer === "string") {
      generatedText.value = value.analysis.answer;
    } else if (!preserveAnswer && (changedThread || changedDraft)) {
      generatedText.value = "";
    }
  }

  function connectStream(value: AgentThread, token: number) {
    if (streamedRunId === value.runId) return;
    eventSource?.close();
    streamedRunId = value.runId;
    const stream = new EventSource(agentApi.eventStreamUrl(value.threadId), {
      withCredentials: true,
    });
    eventSource = stream;
    const current = () =>
      eventSource === stream && isCurrent(token, value.threadId, value.runId);
    const close = () => {
      stream.close();
      if (eventSource === stream) eventSource = null;
    };
    const parse = (event: Event) => {
      try {
        const value = JSON.parse(
          (event as MessageEvent<string>).data,
        ) as unknown;
        return value && typeof value === "object"
          ? (value as Record<string, unknown>)
          : null;
      } catch {
        error.value = "streamFailed";
        return null;
      }
    };

    stream.addEventListener("workflow", (event) => {
      if (!current()) return;
      const payload = parse(event);
      if (
        !payload ||
        payload.runId !== value.runId ||
        typeof payload.seq !== "number" ||
        typeof payload.kind !== "string" ||
        !payload.detail ||
        typeof payload.detail !== "object"
      )
        return;
      if (!events.value.some((item) => item.seq === payload.seq)) {
        events.value = [
          ...events.value,
          payload as unknown as AgentWorkflowEvent,
        ].sort((left, right) => left.seq - right.seq);
      }
    });
    stream.addEventListener("text", (event) => {
      if (!current()) return;
      const payload = parse(event);
      if (payload?.runId === value.runId && typeof payload.text === "string") {
        generatedText.value = payload.text;
      }
    });
    stream.addEventListener("terminal", () => {
      if (!current()) return;
      close();
      void refresh(value.threadId, token, value.runId);
    });
    stream.addEventListener("stream_closed", (event) => {
      if (!current()) return;
      const payload = parse(event);
      close();
      if (payload?.reason === "stream_timeout") error.value = "streamTimedOut";
      if (payload?.reason === "run_replaced") error.value = "runReplaced";
      void refresh(value.threadId, token, value.runId);
    });
    stream.onerror = () => {
      if (!current()) return;
      close();
      error.value = "streamFailed";
      void refresh(value.threadId, token, value.runId);
    };
  }

  async function refresh(
    threadId: string,
    token = runGeneration,
    runId?: string,
  ) {
    try {
      const value = await agentApi.getThread(threadId);
      if (isCurrent(token, threadId, runId)) {
        applyThread(value, true);
        if (
          value.runId !== runId &&
          (value.status === "analyzing" || value.status === "saving")
        ) {
          connectStream(value, token);
        }
      }
    } catch {
      if (token === runGeneration) error.value = "requestFailed";
    }
  }

  async function loadThread(
    threadId: string,
    expectedSourceSubmissionId?: string,
  ) {
    if (saving.value) return null;
    const token = invalidateRun();
    reset();
    loading.value = true;
    try {
      const value = await agentApi.getThread(threadId);
      if (token !== runGeneration) return null;
      if (
        expectedSourceSubmissionId &&
        value.sourceSubmissionId !== expectedSourceSubmissionId
      ) {
        error.value = "sourceMismatch";
        return null;
      }
      applyThread(value);
      if (value.status === "analyzing" || value.status === "saving")
        connectStream(value, token);
      return value;
    } catch {
      if (token === runGeneration) error.value = "requestFailed";
      return null;
    } finally {
      if (token === runGeneration) loading.value = false;
    }
  }

  async function createThread(sourceSubmissionId: string, question: string) {
    const token = invalidateRun();
    reset();
    busy.value = true;
    try {
      const value = await agentApi.createThread(sourceSubmissionId, question);
      if (token !== runGeneration) return null;
      applyThread(value);
      return value;
    } catch {
      if (token === runGeneration) error.value = "requestFailed";
      return null;
    } finally {
      if (token === runGeneration) busy.value = false;
    }
  }

  async function analyze() {
    if (!thread.value) return;
    const { threadId, runId: previousRunId } = thread.value;
    const token = invalidateRun();
    events.value = [];
    generatedText.value = "";
    analyzing.value = true;
    busy.value = true;
    error.value = "";
    const pending = agentApi.analyze(threadId).then(
      (value) => ({ ok: true as const, value }),
      () => ({ ok: false as const }),
    );
    let outcome: Awaited<typeof pending> | null = null;
    while (!outcome && token === runGeneration) {
      outcome = await Promise.race([pending, wait(250).then(() => null)]);
      if (outcome || token !== runGeneration) break;
      try {
        const latest = await agentApi.getThread(threadId);
        if (!isCurrent(token, threadId)) break;
        applyThread(latest, true);
        if (latest.runId !== previousRunId) connectStream(latest, token);
      } catch {
        if (token === runGeneration) error.value = "requestFailed";
      }
    }
    if (token !== runGeneration) return;
    if (outcome?.ok) {
      applyThread(outcome.value, true);
      if (outcome.value.runId !== previousRunId)
        connectStream(outcome.value, token);
    } else if (outcome) {
      error.value = "requestFailed";
      await refresh(threadId, token);
    }
    if (token === runGeneration) {
      analyzing.value = false;
      busy.value = false;
    }
  }

  async function updateDraft() {
    if (!thread.value) return;
    const token = runGeneration;
    const current = thread.value;
    busy.value = true;
    error.value = "";
    try {
      const value = await agentApi.updateDraft(current.threadId, {
        draftVersion: current.draft.draftVersion,
        title: draftTitle.value,
        content: draftContent.value,
      });
      if (isCurrent(token, current.threadId)) applyThread(value);
    } catch {
      if (token === runGeneration) error.value = "requestFailed";
    } finally {
      if (token === runGeneration) busy.value = false;
    }
  }

  async function confirmDraft() {
    if (!thread.value || busy.value) return;
    const token = runGeneration;
    const current = thread.value;
    busy.value = true;
    error.value = "";
    try {
      const value = await agentApi.confirm(
        current.threadId,
        current.draft,
        current.paramsDigest,
      );
      if (isCurrent(token, current.threadId)) applyThread(value);
    } catch {
      if (token === runGeneration) {
        error.value = "requestFailed";
        await refresh(current.threadId, token);
      }
    } finally {
      if (token === runGeneration) busy.value = false;
    }
  }

  async function save() {
    const confirmationId = thread.value?.confirmation?.id;
    if (!thread.value || !confirmationId || busy.value) return;
    const token = runGeneration;
    const current = thread.value;
    busy.value = true;
    saving.value = true;
    error.value = "";
    try {
      const value = await agentApi.save(current.threadId, confirmationId);
      if (isCurrent(token, current.threadId)) applyThread(value);
    } catch {
      if (token === runGeneration) {
        error.value = "requestFailed";
        await refresh(current.threadId, token);
      }
    } finally {
      if (token === runGeneration) {
        busy.value = false;
        saving.value = false;
      }
    }
  }

  async function recover() {
    if (!thread.value) return;
    const token = runGeneration;
    const current = thread.value;
    busy.value = true;
    error.value = "";
    try {
      const value = await agentApi.recover(current.threadId);
      if (isCurrent(token, current.threadId)) applyThread(value);
    } catch {
      if (token === runGeneration) {
        error.value = "requestFailed";
        await refresh(current.threadId, token);
      }
    } finally {
      if (token === runGeneration) busy.value = false;
    }
  }

  async function cancel() {
    if (!thread.value || saving.value || thread.value.status === "saving")
      return;
    const token = invalidateRun();
    const current = thread.value;
    canceling.value = true;
    error.value = "";
    try {
      const value = await agentApi.cancel(current.threadId);
      if (isCurrent(token, current.threadId)) applyThread(value, true);
    } catch {
      if (token === runGeneration) {
        error.value = "requestFailed";
        await refresh(current.threadId, token);
      }
    } finally {
      if (token === runGeneration) canceling.value = false;
    }
  }

  function clear() {
    if (saving.value || thread.value?.status === "saving") return false;
    invalidateRun();
    reset();
    return true;
  }

  onScopeDispose(invalidateRun);

  return {
    thread,
    events,
    generatedText,
    draftTitle,
    draftContent,
    error,
    busy,
    loading,
    analyzing,
    canceling,
    saving,
    loadThread,
    createThread,
    analyze,
    updateDraft,
    confirmDraft,
    save,
    recover,
    cancel,
    clear,
  };
}
