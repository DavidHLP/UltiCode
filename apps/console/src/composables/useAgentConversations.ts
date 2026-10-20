import { computed, onScopeDispose, ref } from "vue";
import {
  agentApi,
  type AgentConversation,
  type AgentConversationTurn,
  type AgentWorkflowEvent,
} from "@/api/agent";

const HISTORY_PAGE_SIZE = 20;
const TURN_PAGE_SIZE = 30;
const MAX_STREAM_ERRORS = 5;
const MAX_STREAM_OBSERVATION_MS = 2 * 60 * 1000;

export function useAgentConversations() {
  const conversations = ref<AgentConversation[]>([]);
  const conversation = ref<AgentConversation | null>(null);
  const turns = ref<AgentConversationTurn[]>([]);
  const events = ref<AgentWorkflowEvent[]>([]);
  const sourceSubmissionId = ref("");
  const error = ref("");
  const loadingHistory = ref(false);
  const loadingConversation = ref(false);
  const busy = ref(false);
  const canceling = ref(false);
  const deleting = ref(false);
  const hasMoreConversations = ref(true);
  const hasMoreTurns = ref(false);
  const activeTurnId = ref<string | null>(null);
  let historyOffset = 0;
  let generation = 0;
  let eventSource: EventSource | null = null;
  let observationTimer: ReturnType<typeof setTimeout> | null = null;

  const currentTurn = computed(
    () =>
      turns.value.find((turn) => turn.turnId === activeTurnId.value) ?? null,
  );

  function closeStream() {
    eventSource?.close();
    eventSource = null;
    if (observationTimer !== null) clearTimeout(observationTimer);
    observationTimer = null;
  }

  function invalidate() {
    generation += 1;
    closeStream();
    busy.value = false;
    canceling.value = false;
    loadingConversation.value = false;
    return generation;
  }

  function upsertTurn(turn: AgentConversationTurn) {
    const index = turns.value.findIndex((item) => item.turnId === turn.turnId);
    if (index < 0)
      turns.value = [...turns.value, turn].sort(
        (a, b) => a.turnIndex - b.turnIndex,
      );
    else turns.value[index] = turn;
  }

  function upsertConversation(value: AgentConversation) {
    const index = conversations.value.findIndex(
      (item) => item.conversationId === value.conversationId,
    );
    if (index < 0) conversations.value = [value, ...conversations.value];
    else conversations.value[index] = value;
    conversations.value.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  }

  async function loadConversations(reset = false) {
    if (loadingHistory.value) return;
    loadingHistory.value = true;
    const offset = reset ? 0 : historyOffset;
    try {
      const page = await agentApi.listConversations(HISTORY_PAGE_SIZE, offset);
      conversations.value = reset
        ? page.conversations
        : [
            ...conversations.value,
            ...page.conversations.filter(
              (item) =>
                !conversations.value.some(
                  (existing) => existing.conversationId === item.conversationId,
                ),
            ),
          ];
      historyOffset = page.nextOffset ?? offset + page.conversations.length;
      hasMoreConversations.value = page.nextOffset !== null;
    } catch {
      error.value = "requestFailed";
    } finally {
      loadingHistory.value = false;
    }
  }

  async function refreshConversation(token = generation) {
    const current = conversation.value;
    if (!current) return null;
    try {
      const page = await agentApi.getConversation(
        current.conversationId,
        TURN_PAGE_SIZE,
      );
      if (
        token !== generation ||
        conversation.value?.conversationId !== current.conversationId
      )
        return null;
      conversation.value = page.conversation;
      turns.value = page.turns;
      hasMoreTurns.value = page.hasMore;
      upsertConversation(page.conversation);
      return page;
    } catch {
      if (token === generation) error.value = "requestFailed";
      return null;
    }
  }

  function connectStream(
    turn: AgentConversationTurn,
    token: number,
    observation = { failures: 0, startedAt: Date.now() },
  ) {
    if (
      observation.failures >= MAX_STREAM_ERRORS ||
      Date.now() - observation.startedAt >= MAX_STREAM_OBSERVATION_MS
    ) {
      closeStream();
      error.value = "streamFailed";
      return;
    }
    eventSource?.close();
    eventSource = null;
    const stream = new EventSource(
      agentApi.conversationEventStreamUrl(
        turn.conversationId,
        turn.turnId,
        turn.eventSeq,
      ),
      { withCredentials: true },
    );
    eventSource = stream;
    const isTurnCurrent = () =>
      token === generation && activeTurnId.value === turn.turnId;
    const isCurrent = () => isTurnCurrent() && eventSource === stream;
    if (!observationTimer) {
      observationTimer = setTimeout(
        () => {
          if (isTurnCurrent()) {
            closeStream();
            error.value = "streamFailed";
          }
          observationTimer = null;
        },
        Math.max(
          0,
          MAX_STREAM_OBSERVATION_MS - (Date.now() - observation.startedAt),
        ),
      );
    }
    const close = () => {
      stream.close();
      if (eventSource === stream) eventSource = null;
    };
    const refreshStatus = async (reconnect = false) => {
      const page = await refreshConversation(token);
      if (!isTurnCurrent() || (eventSource && eventSource !== stream)) return;
      const latest = page?.turns.find((item) => item.turnId === turn.turnId);
      if (latest && !["queued", "running"].includes(latest.status)) {
        closeStream();
        busy.value = false;
        activeTurnId.value = null;
      } else if (reconnect) {
        connectStream(latest ?? turn, token, observation);
      }
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
      if (!isCurrent()) return;
      const payload = parse(event);
      if (
        !payload ||
        payload.runId !== turn.runId ||
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
    stream.addEventListener("terminal", (event) => {
      if (!isCurrent()) return;
      const payload = parse(event);
      if (payload?.runId !== turn.runId) return;
      closeStream();
      void refreshConversation(token).finally(() => {
        if (token === generation && activeTurnId.value === turn.turnId) {
          busy.value = false;
          activeTurnId.value = null;
        }
      });
    });
    stream.addEventListener("stream_closed", (event) => {
      if (!isCurrent()) return;
      const payload = parse(event);
      close();
      if (payload?.reason === "stream_timeout") error.value = "streamTimedOut";
      if (payload?.reason === "run_replaced") error.value = "runReplaced";
      void refreshStatus(true);
    });
    stream.onerror = () => {
      if (!isCurrent()) return;
      error.value = "streamFailed";
      observation.failures += 1;
      if (
        observation.failures >= MAX_STREAM_ERRORS ||
        Date.now() - observation.startedAt >= MAX_STREAM_OBSERVATION_MS
      ) {
        closeStream();
        void refreshStatus();
        return;
      }
      // EventSource may reconnect transient failures; stop it after bounded errors or time.
      void refreshStatus();
    };
  }

  async function runTurn(turn: AgentConversationTurn, sourceConsent: boolean) {
    const token = generation;
    activeTurnId.value = turn.turnId;
    upsertTurn({ ...turn, status: "running" });
    events.value = [];
    error.value = "";
    busy.value = true;
    connectStream(turn, token);
    let preserveObservation = false;
    try {
      const result = await agentApi.runConversationTurn(
        turn.conversationId,
        turn.turnId,
        sourceConsent,
      );
      if (token !== generation) return;
      upsertTurn(result);
      if (conversation.value)
        upsertConversation({
          ...conversation.value,
          turnCount: Math.max(conversation.value.turnCount, result.turnIndex),
          lastQuestion: result.question,
          updatedAt: result.updatedAt,
        });
      await loadConversations(true);
    } catch {
      if (token === generation) {
        error.value = "requestFailed";
        const page = await refreshConversation(token);
        const observedTurn = page?.turns.find(
          (item) => item.turnId === turn.turnId,
        );
        preserveObservation =
          !observedTurn || ["queued", "running"].includes(observedTurn.status);
      }
    } finally {
      if (
        token === generation &&
        activeTurnId.value === turn.turnId &&
        !preserveObservation
      ) {
        busy.value = false;
        activeTurnId.value = null;
        closeStream();
      }
    }
  }

  async function createConversation(
    submissionId: string,
    question: string,
    sourceConsent: boolean,
  ) {
    if (busy.value) return;
    const token = invalidate();
    sourceSubmissionId.value = submissionId;
    error.value = "";
    try {
      const result = await agentApi.createConversation({
        conversationId: crypto.randomUUID(),
        sourceSubmissionId: submissionId,
        clientTurnId: crypto.randomUUID(),
        question,
      });
      if (token !== generation) return;
      conversation.value = result.conversation;
      turns.value = [result.turn];
      hasMoreTurns.value = false;
      upsertConversation(result.conversation);
      await runTurn(result.turn, sourceConsent);
    } catch {
      if (token === generation) error.value = "requestFailed";
    }
  }

  async function sendTurn(question: string, sourceConsent: boolean) {
    if (busy.value || !question.trim()) return;
    if (!conversation.value) {
      if (sourceSubmissionId.value)
        await createConversation(
          sourceSubmissionId.value,
          question.trim(),
          sourceConsent,
        );
      else error.value = "submissionRequired";
      return;
    }
    const token = generation;
    error.value = "";
    try {
      const turn = await agentApi.createConversationTurn(
        conversation.value.conversationId,
        crypto.randomUUID(),
        question.trim(),
      );
      if (token !== generation) return;
      upsertTurn(turn);
      await runTurn(turn, sourceConsent);
    } catch {
      if (token === generation) error.value = "requestFailed";
    }
  }

  async function openConversation(conversationId: string) {
    const token = invalidate();
    conversation.value = null;
    sourceSubmissionId.value = "";
    turns.value = [];
    events.value = [];
    error.value = "";
    loadingConversation.value = true;
    try {
      const page = await agentApi.getConversation(
        conversationId,
        TURN_PAGE_SIZE,
      );
      if (token !== generation) return;
      conversation.value = page.conversation;
      sourceSubmissionId.value = page.conversation.sourceSubmissionId;
      turns.value = page.turns;
      hasMoreTurns.value = page.hasMore;
      upsertConversation(page.conversation);
      const active = [...page.turns]
        .reverse()
        .find((turn) => turn.status === "running");
      if (active) {
        activeTurnId.value = active.turnId;
        busy.value = true;
        try {
          const recovered = await agentApi.recoverConversationTurn(
            conversationId,
            active.turnId,
          );
          if (token !== generation) return;
          upsertTurn(recovered);
          if (recovered.status === "running") {
            busy.value = true;
            connectStream(recovered, token);
          } else {
            busy.value = false;
            activeTurnId.value = null;
          }
        } catch {
          if (token === generation) error.value = "requestFailed";
        }
      }
    } catch {
      if (token === generation) error.value = "requestFailed";
    } finally {
      if (token === generation) loadingConversation.value = false;
    }
  }

  async function loadOlderTurns() {
    const current = conversation.value;
    const first = turns.value[0];
    if (!current || !first || !hasMoreTurns.value || loadingConversation.value)
      return;
    loadingConversation.value = true;
    try {
      const page = await agentApi.getConversation(
        current.conversationId,
        TURN_PAGE_SIZE,
        first.turnIndex,
      );
      if (conversation.value?.conversationId !== current.conversationId) return;
      turns.value = [
        ...page.turns,
        ...turns.value.filter(
          (turn) => !page.turns.some((older) => older.turnId === turn.turnId),
        ),
      ].sort((a, b) => a.turnIndex - b.turnIndex);
      hasMoreTurns.value = page.hasMore;
    } catch {
      error.value = "requestFailed";
    } finally {
      loadingConversation.value = false;
    }
  }

  async function retryTurn(
    turn: AgentConversationTurn,
    sourceConsent: boolean,
  ) {
    if (busy.value || !["queued", "failed"].includes(turn.status)) return;
    await runTurn(turn, sourceConsent);
  }

  async function cancelTurn() {
    const current = currentTurn.value;
    if (!current || canceling.value) return;
    canceling.value = true;
    try {
      upsertTurn(
        await agentApi.cancelConversationTurn(
          current.conversationId,
          current.turnId,
        ),
      );
    } catch {
      error.value = "requestFailed";
    } finally {
      canceling.value = false;
    }
  }

  async function deleteCurrentConversation() {
    const current = conversation.value;
    if (!current || busy.value || deleting.value) return false;
    deleting.value = true;
    try {
      await agentApi.deleteConversation(current.conversationId);
      conversations.value = conversations.value.filter(
        (item) => item.conversationId !== current.conversationId,
      );
      conversation.value = null;
      turns.value = [];
      events.value = [];
      return true;
    } catch {
      error.value = "requestFailed";
      return false;
    } finally {
      deleting.value = false;
    }
  }

  function startNewConversation(submissionId = sourceSubmissionId.value) {
    if (busy.value) return false;
    invalidate();
    conversation.value = null;
    turns.value = [];
    events.value = [];
    sourceSubmissionId.value = submissionId;
    error.value = "";
    return true;
  }

  onScopeDispose(invalidate);

  return {
    conversations,
    conversation,
    turns,
    events,
    sourceSubmissionId,
    error,
    loadingHistory,
    loadingConversation,
    busy,
    canceling,
    deleting,
    hasMoreConversations,
    hasMoreTurns,
    currentTurn,
    loadConversations,
    openConversation,
    loadOlderTurns,
    sendTurn,
    retryTurn,
    cancelTurn,
    deleteCurrentConversation,
    startNewConversation,
  };
}
