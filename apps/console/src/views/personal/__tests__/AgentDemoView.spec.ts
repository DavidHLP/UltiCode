import { flushPromises, mount } from "@vue/test-utils";
import { describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  query: {} as Record<string, string>,
  getThread: vi.fn(),
  replace: vi.fn(),
}));
vi.mock("@/api/agent", () => ({ agentApi: { getThread: mocks.getThread } }));
vi.mock("vue-router", () => ({
  useRoute: () => ({ query: mocks.query }),
  useRouter: () => ({ replace: mocks.replace }),
}));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));
import AgentDemoView from "../AgentDemoView.vue";

describe("legacy Agent demo route", () => {
  it("returns to personal submissions without a thread id", async () => {
    mocks.query = {};
    mocks.replace.mockClear();
    const wrapper = mount(AgentDemoView);
    await flushPromises();
    expect(mocks.getThread).not.toHaveBeenCalled();
    expect(mocks.replace).toHaveBeenCalledWith({ name: "personal-submissions" });
    wrapper.unmount();
  });

  it("restores a valid thread through its owner submission detail", async () => {
    const threadId = "12345678-1234-1234-1234-123456789abc";
    const sourceSubmissionId = "abcdef12-1234-1234-1234-123456789abc";
    mocks.query = { threadId };
    mocks.getThread.mockResolvedValueOnce({ sourceSubmissionId });
    mocks.replace.mockClear();
    const wrapper = mount(AgentDemoView);
    await flushPromises();
    expect(mocks.getThread).toHaveBeenCalledWith(threadId);
    expect(mocks.replace).toHaveBeenCalledWith({
      name: "submission-detail",
      params: { id: sourceSubmissionId },
      query: { threadId },
    });
    wrapper.unmount();
  });
});
