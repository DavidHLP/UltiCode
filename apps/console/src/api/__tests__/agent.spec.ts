import { beforeEach, describe, expect, it, vi } from "vitest";
import { apiGet, apiPost, apiPut } from "@/utils/request";
import { agentApi } from "@/api/agent";

vi.mock("@/utils/request", () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
  apiPut: vi.fn(),
}));

describe("agentApi", () => {
  beforeEach(() => vi.clearAllMocks());

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

  it("encodes thread IDs for reads and event streams", async () => {
    vi.mocked(apiGet).mockResolvedValue({});
    await agentApi.getThread("thread/with space");
    expect(apiGet).toHaveBeenCalledWith("/agent/threads/thread%2Fwith%20space");
    expect(agentApi.eventStreamUrl("thread/with space")).toBe(
      "/api/agent/threads/thread%2Fwith%20space/events/stream?after=0",
    );
  });
});
