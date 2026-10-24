import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiDelete, apiGet, apiPost, apiPut } from "@/utils/request";
import { agentApi } from "@/api/agent";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
  apiDelete: vi.fn(),
}));

describe("agentApi", () => {
  beforeEach(() => vi.clearAllMocks());

  it("sends only selected context references with each turn", async () => {
    vi.mocked(apiPost).mockResolvedValue({});
    const context = {
      includeProblem: true,
      comments: [{ solutionId: "solution-1", commentId: "comment-1" }],
    };
    await agentApi.createConversationTurn(
      "conversation-1",
      "client-turn-1",
      "Explain this comment",
      context,
    );
    expect(apiPost).toHaveBeenCalledWith(
      "/agent/conversations/conversation-1/turns",
      {
        clientTurnId: "client-turn-1",
        question: "Explain this comment",
        context,
      },
    );
  });

  it("reads capabilities and an owned saved learning plan through the request helper", async () => {
    vi.mocked(apiGet).mockResolvedValue({ analysisAvailable: true, persistenceAvailable: false, sourceMode: "metadata" });
    await agentApi.getCapabilities();
    expect(apiGet).toHaveBeenCalledWith("/agent/capabilities");
    await agentApi.getLearningPlan("plan/one");
    expect(apiGet).toHaveBeenLastCalledWith("/learning-plans/plan%2Fone");
  });

  it("uses the authenticated Agent workflow endpoints and explicit confirmation payload", async () => {
    const thread = { threadId: "thread-1" };
    vi.mocked(apiPost).mockResolvedValue(thread);
    vi.mocked(apiPut).mockResolvedValue(thread);
    vi.mocked(apiGet).mockResolvedValue(thread);

    await agentApi.createThread("submission-1", "review this");
    expect(apiPost).toHaveBeenCalledWith("/agent/threads", {
      sourceSubmissionId: "submission-1",
      question: "review this",
    });
    await agentApi.confirm("thread-1", {
      draftVersion: 2,
      title: "Title",
      content: "Content",
      facts: [],
      hypotheses: [],
      citations: [],
      citationChecks: [],
      sampleScope: null,
    }, "digest");
    expect(apiPost).toHaveBeenLastCalledWith("/agent/threads/thread-1/confirm", {
      draftVersion: 2,
      paramsDigest: "digest",
      confirm: true,
    });
    await agentApi.save("thread-1", "confirmation-1");
    expect(apiPost).toHaveBeenLastCalledWith("/agent/threads/thread-1/save", {
      confirmationId: "confirmation-1",
    });
    await agentApi.recover("thread-1");
    expect(apiPost).toHaveBeenLastCalledWith("/agent/threads/thread-1/recover", { retry: false });
    await agentApi.cancel("thread-1");
    expect(apiPost).toHaveBeenLastCalledWith("/agent/threads/thread-1/cancel", {});
    await agentApi.updateDraft("thread-1", { draftVersion: 2, title: "Title", content: "Content" });
    expect(apiPut).toHaveBeenCalledWith("/agent/threads/thread-1/draft", {
      draftVersion: 2,
      title: "Title",
      content: "Content",
    });
  });

  it("uses conversation endpoints for create, history, turn lifecycle, and deletion", async () => {
    vi.mocked(apiGet).mockResolvedValue({});
    vi.mocked(apiPost).mockResolvedValue({});
    vi.mocked(apiDelete).mockResolvedValue({});

    await agentApi.createConversation({
      conversationId: "conversation-1",
      sourceSubmissionId: "submission-1",
      clientTurnId: "turn-client-1",
      question: "Why did this fail?",
    });
    expect(apiPost).toHaveBeenCalledWith("/agent/conversations", {
      conversationId: "conversation-1",
      sourceSubmissionId: "submission-1",
      clientTurnId: "turn-client-1",
      question: "Why did this fail?",
    });
    await agentApi.listConversations(10, 20);
    expect(apiGet).toHaveBeenCalledWith("/agent/conversations?limit=10&offset=20");
    await agentApi.getConversation("conversation/one", 20, 4);
    expect(apiGet).toHaveBeenLastCalledWith("/agent/conversations/conversation%2Fone?limit=20&before=4");
    await agentApi.createConversationTurn("conversation/one", "client-turn-2", "Follow up");
    expect(apiPost).toHaveBeenLastCalledWith("/agent/conversations/conversation%2Fone/turns", {
      clientTurnId: "client-turn-2",
      question: "Follow up",
    });
    await agentApi.runConversationTurn("conversation/one", "turn/2", true, "run-1");
    expect(apiPost).toHaveBeenLastCalledWith("/agent/conversations/conversation%2Fone/turns/turn%2F2/run", { sourceConsent: true, expectedRunId: "run-1" });
    await agentApi.cancelConversationTurn("conversation-1", "turn-1", "run-1");
    expect(apiPost).toHaveBeenLastCalledWith("/agent/conversations/conversation-1/turns/turn-1/cancel", { expectedRunId: "run-1" });
    await agentApi.recoverConversationTurn("conversation-1", "turn-1");
    expect(apiPost).toHaveBeenLastCalledWith("/agent/conversations/conversation-1/turns/turn-1/recover", {});
    await agentApi.deleteConversation("conversation-1");
    expect(apiDelete).toHaveBeenCalledWith("/agent/conversations/conversation-1");
    expect(agentApi.conversationEventStreamUrl("conversation/one", "turn/2", 7, "run/1")).toBe(
      "/api/agent/conversations/conversation%2Fone/turns/turn%2F2/events/stream?after=7&runId=run%2F1",
    );
  });
});
