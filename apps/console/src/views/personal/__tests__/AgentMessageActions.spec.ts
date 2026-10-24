import { mount, flushPromises } from "@vue/test-utils";
import { ref } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { AgentConversationTurn } from "@/api/agent";
const mocks = vi.hoisted(() => ({ copy: vi.fn() }));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));
vi.mock("@vueuse/core", async (original) => ({
  ...(await original<typeof import("@vueuse/core")>()),
  useClipboard: () => ({ copy: mocks.copy, copied: ref(false) }),
}));
import AgentMessageActions from "../components/AgentMessageActions.vue";

const turn = {
  question: "Explain this result",
  answer: "**A verified answer**",
} as AgentConversationTurn;
describe("AgentMessageActions", () => {
  beforeEach(() => mocks.copy.mockReset().mockResolvedValue(undefined));
  it("copies the answer text and reports clipboard failure", async () => {
    const wrapper = mount(AgentMessageActions, {
      props: { turn, disabled: false, hasDraft: false },
    });
    await wrapper
      .get('[aria-label="agent.workspace.copyAnswer"]')
      .trigger("click");
    await flushPromises();
    expect(mocks.copy).toHaveBeenCalledWith(turn.answer);
    mocks.copy.mockRejectedValueOnce(new Error("clipboard blocked"));
    await wrapper
      .get('[aria-label="agent.workspace.copyAnswer"]')
      .trigger("click");
    await flushPromises();
    expect(wrapper.get('[role="status"]').text()).toBe(
      "agent.workspace.copyFailed",
    );
    wrapper.unmount();
  });
  it("reuses a question only when idle with an empty draft", async () => {
    const wrapper = mount(AgentMessageActions, {
      props: { turn, disabled: false, hasDraft: false },
    });
    await wrapper
      .get('[aria-label="agent.workspace.reuseQuestion"]')
      .trigger("click");
    expect(wrapper.emitted("reuse")?.[0]).toEqual([turn]);
    await wrapper.setProps({ hasDraft: true });
    expect(
      wrapper
        .get('[aria-label="agent.workspace.reuseQuestion"]')
        .attributes("disabled"),
    ).toBeDefined();
    await wrapper.setProps({ hasDraft: false, disabled: true });
    expect(
      wrapper
        .get('[aria-label="agent.workspace.reuseQuestion"]')
        .attributes("disabled"),
    ).toBeDefined();
    wrapper.unmount();
  });
});
