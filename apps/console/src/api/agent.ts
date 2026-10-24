import { apiDelete, apiGet, apiPost, apiPut } from "@/utils/request";

export type AgentThreadStatus =
  | "awaiting_confirmation"
  | "analyzing"
  | "confirmed"
  | "saving"
  | "saved"
  | "unknown"
  | "failed"
  | "cancelled";

export interface AgentCitation {
  claim: string;
  chunk_id: string;
  doc_id: string;
  version: string;
  source_path: string;
  source_position: string;
  access_scope: string;
  sample_kind: string;
  source_trust: string;
  text: string;
}

export interface AgentWorkflowEvent {
  seq: number;
  kind: string;
  detail: Record<string, unknown>;
  createdAt: number;
}

export interface AgentCapabilities {
  featureEnabled?: boolean;
  analysisAvailable: boolean;
  persistenceAvailable: boolean;
  conversationHistoryAvailable: boolean;
  sourceMode: "metadata" | "owner_source";
}

export interface AgentConversation {
  conversationId: string;
  sourceSubmissionId: string;
  title: string;
  turnCount: number;
  lastQuestion: string;
  createdAt: string | number;
  updatedAt: string | number;
}

export interface AgentAnalysisContext {
  includeProblem: boolean;
  comments: Array<{ solutionId: string; postId?: never; commentId: string } | { postId: string; solutionId?: never; commentId: string }>;
}

export type AgentConversationTurnStatus = "queued" | "running" | "completed" | "failed" | "cancelled";

export interface AgentConversationCitation {
  claim: string;
  chunk_id: string;
  doc_id: string;
  version: string;
  source_path: string;
  source_position: string;
  access_scope: string;
  sample_kind: string;
  source_trust: string;
}

export interface AgentConversationTurn {
  context?: AgentAnalysisContext | Record<string, never>;
  turnId: string;
  conversationId: string;
  turnIndex: number;
  runId: string;
  status: AgentConversationTurnStatus;
  question: string;
  answer: string | null;
  citations: AgentConversationCitation[];
  trace: Array<{ tool_name?: string; failed?: boolean }>;
  failureReason: string | null;
  cancelRequested: boolean;
  eventSeq: number;
  createdAt: string | number;
  updatedAt: string | number;
}

export interface AgentConversationPage {
  conversation: AgentConversation;
  turns: AgentConversationTurn[];
  hasMore: boolean;
}

export interface AgentConversationList {
  conversations: AgentConversation[];
  nextOffset: number | null;
}

export interface AgentLearningPlan {
  id: string;
  sourceSubmissionId: string;
  draftVersion: number;
  title: string;
  content: string;
  createdAt: string;
}

export interface AgentThread {
  threadId: string;
  runId: string;
  status: AgentThreadStatus;
  sourceSubmissionId: string;
  question: string;
  draft: {
    draftVersion: number;
    title: string;
    content: string;
    facts: unknown[];
    hypotheses: unknown[];
    citations: AgentCitation[];
    citationChecks: Array<Record<string, unknown>>;
    sampleScope: string | null;
    model_answer?: string;
  };
  paramsDigest: string;
  analysis: {
    answer?: string;
    trace?: Array<{ tool_name?: string; failed?: boolean }>;
    citation_checks?: Array<Record<string, unknown>>;
  } | null;
  confirmation: {
    id?: string;
    action?: string;
    draftVersion?: number;
    paramsDigest?: string;
    expiresAt?: number;
  } | null;
  receipt: {
    planId: string | null;
    cancelRequested: boolean;
    failureReason: string | null;
  };
}

const threadPath = (threadId: string) =>
  `/agent/threads/${encodeURIComponent(threadId)}`;
const conversationPath = (conversationId: string) =>
  `/agent/conversations/${encodeURIComponent(conversationId)}`;
const conversationTurnPath = (conversationId: string, turnId: string) =>
  `${conversationPath(conversationId)}/turns/${encodeURIComponent(turnId)}`;

export const agentApi = {
  getCapabilities() {
    return apiGet<AgentCapabilities>("/agent/capabilities");
  },
  createConversation(input: {
    conversationId: string;
    sourceSubmissionId: string;
    clientTurnId: string;
    question: string;
    context?: AgentAnalysisContext;
  }) {
    return apiPost<{ conversation: AgentConversation; turn: AgentConversationTurn }>(
      "/agent/conversations",
      input,
    );
  },
  listConversations(limit = 20, offset = 0) {
    return apiGet<AgentConversationList>(`/agent/conversations?limit=${limit}&offset=${offset}`);
  },
  getConversation(conversationId: string, limit = 30, before?: number) {
    const query = new URLSearchParams({ limit: String(limit) });
    if (before !== undefined) query.set("before", String(before));
    return apiGet<AgentConversationPage>(`${conversationPath(conversationId)}?${query}`);
  },
  createConversationTurn(conversationId: string, clientTurnId: string, question: string, context?: AgentAnalysisContext) {
    return apiPost<AgentConversationTurn>(`${conversationPath(conversationId)}/turns`, {
      clientTurnId,
      question,
      ...(context ? { context } : {}),
    });
  },
  runConversationTurn(conversationId: string, turnId: string, sourceConsent: boolean, expectedRunId: string) {
    return apiPost<AgentConversationTurn>(`${conversationTurnPath(conversationId, turnId)}/run`, {
      sourceConsent,
      expectedRunId,
    });
  },
  cancelConversationTurn(conversationId: string, turnId: string, expectedRunId: string) {
    return apiPost<AgentConversationTurn>(`${conversationTurnPath(conversationId, turnId)}/cancel`, { expectedRunId });
  },
  recoverConversationTurn(conversationId: string, turnId: string) {
    return apiPost<AgentConversationTurn>(`${conversationTurnPath(conversationId, turnId)}/recover`, {});
  },
  deleteConversation(conversationId: string) {
    return apiDelete<{ conversationId: string; deleted: boolean }>(conversationPath(conversationId));
  },
  conversationEventStreamUrl(conversationId: string, turnId: string, after = 0, runId?: string) {
    const base = (import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/$/, "");
    const query = new URLSearchParams({ after: String(after) });
    if (runId) query.set("runId", runId);
    return `${base}${conversationTurnPath(conversationId, turnId)}/events/stream?${query}`;
  },
  getLearningPlan(planId: string) {
    return apiGet<AgentLearningPlan>(`/learning-plans/${encodeURIComponent(planId)}`);
  },
  createThread(sourceSubmissionId: string, question: string) {
    return apiPost<AgentThread>("/agent/threads", { sourceSubmissionId, question });
  },
  getThread(threadId: string) {
    return apiGet<AgentThread>(threadPath(threadId));
  },
  analyze(threadId: string) {
    return apiPost<AgentThread>(`${threadPath(threadId)}/analyze`, {});
  },
  updateDraft(
    threadId: string,
    draft: { draftVersion: number; title: string; content: string },
  ) {
    return apiPut<AgentThread>(`${threadPath(threadId)}/draft`, draft);
  },
  confirm(threadId: string, draft: AgentThread["draft"], paramsDigest: string) {
    return apiPost<AgentThread>(`${threadPath(threadId)}/confirm`, {
      draftVersion: draft.draftVersion,
      paramsDigest,
      confirm: true,
    });
  },
  save(threadId: string, confirmationId: string) {
    return apiPost<AgentThread>(`${threadPath(threadId)}/save`, { confirmationId });
  },
  recover(threadId: string) {
    return apiPost<AgentThread>(`${threadPath(threadId)}/recover`, { retry: false });
  },
  cancel(threadId: string) {
    return apiPost<AgentThread>(`${threadPath(threadId)}/cancel`, {});
  },
  eventStreamUrl(threadId: string) {
    const base = (import.meta.env.VITE_API_BASE_URL || "/api").replace(/\/$/, "");
    return `${base}${threadPath(threadId)}/events/stream?after=0`;
  },
};
