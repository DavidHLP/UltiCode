import { apiGet, apiPost, apiPut } from "@/utils/request";

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
  analysisAvailable: boolean;
  persistenceAvailable: boolean;
  sourceMode: "metadata" | "owner_source";
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

export const agentApi = {
  getCapabilities() {
    return apiGet<AgentCapabilities>("/agent/capabilities");
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
