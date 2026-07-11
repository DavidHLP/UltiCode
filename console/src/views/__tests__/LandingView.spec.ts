import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import LandingView from "../LandingView.vue";

const push = vi.fn();

vi.mock("vue-router", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/stores/auth", () => ({
  useAuthStore: () => ({ isAuthenticated: false }),
}));

vi.mock("vue-i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

const mountView = () =>
  mount(LandingView, {
    global: {
      stubs: {
        RouterLink: {
          props: ["to"],
          template: '<a :data-to="JSON.stringify(to)"><slot /></a>',
        },
      },
    },
  });

describe("LandingView", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    push.mockClear();
  });
  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it("exposes the selected language to assistive technology", async () => {
    const wrapper = mountView();
    const languageButtons = wrapper.findAll(".language-tab");

    expect(languageButtons[0].attributes("aria-pressed")).toBe("true");
    await languageButtons[1].trigger("click");
    expect(languageButtons[0].attributes("aria-pressed")).toBe("false");
    expect(languageButtons[1].attributes("aria-pressed")).toBe("true");
  });

  it("swaps the code body when a different language is selected", async () => {
    const wrapper = mountView();
    const code = () => wrapper.get("pre code").text();

    expect(code()).toContain("vector<int> twoSum");

    await wrapper.findAll(".language-tab")[1].trigger("click");
    expect(code()).toContain("def two_sum");

    await wrapper.findAll(".language-tab")[2].trigger("click");
    expect(code()).toContain("function twoSum");
  });

  it("links the trial call to action directly to the sample problem", () => {
    const wrapper = mountView();
    const trialLinks = wrapper
      .findAll("a")
      .filter((link) => link.text().includes("landing.tryProblem"));

    expect(trialLinks).toHaveLength(2);
    for (const link of trialLinks) {
      expect(link.attributes("data-to")).toContain('"name":"problem-detail"');
      expect(link.attributes("data-to")).toContain('"slug":"two-sum"');
    }
  });

  it("routes the primary guest CTA straight into the seeded problem", async () => {
    const wrapper = mountView();
    const cta = wrapper
      .findAll("button")
      .find(
        (btn) =>
          btn.text().includes("landing.freeStart") &&
          !btn.classes().includes("button--compact"),
      );
    expect(cta).toBeDefined();
    await cta!.trigger("click");

    expect(push).toHaveBeenCalledWith({
      name: "problem-detail",
      params: { slug: "two-sum" },
    });
  });

  it("prevents overlapping runs and renders the complete result", async () => {
    const wrapper = mountView();
    const runButton = wrapper.get('[data-testid="run-simulation"]');

    await runButton.trigger("click");
    await runButton.trigger("click");
    expect(runButton.attributes("disabled")).toBeDefined();
    expect(vi.getTimerCount()).toBe(3);

    await vi.runAllTimersAsync();
    expect(wrapper.text()).toContain("landing.outputComplete");
    expect(wrapper.text()).toContain("landing.compileSuccess");
    expect(runButton.attributes("disabled")).toBeUndefined();
  });

  it("clears pending simulation work when unmounted", async () => {
    const wrapper = mountView();
    await wrapper.get('[data-testid="run-simulation"]').trigger("click");
    expect(vi.getTimerCount()).toBe(3);

    wrapper.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it("switches use-case content with accessible tab state", async () => {
    const wrapper = mountView();
    const tabs = wrapper.findAll('[role="tab"]');

    expect(tabs).toHaveLength(4);
    expect(tabs[0].attributes("aria-selected")).toBe("true");
    await tabs[2].trigger("click");
    expect(tabs[0].attributes("aria-selected")).toBe("false");
    expect(tabs[2].attributes("aria-selected")).toBe("true");
    expect(wrapper.get('[role="tabpanel"]').text()).toContain(
      "landing.usecase.contest.title",
    );

    const focus = vi.spyOn(tabs[3].element as HTMLButtonElement, "focus");
    await tabs[2].trigger("keydown", { key: "End" });
    await vi.runAllTimersAsync();
    expect(tabs[3].attributes("aria-selected")).toBe("true");
    expect(tabs[3].attributes("tabindex")).toBe("0");
    expect(tabs[2].attributes("tabindex")).toBe("-1");
    expect(focus).toHaveBeenCalledOnce();
  });

  it("links every proof item to a verifiable product surface", () => {
    const wrapper = mountView();
    const proofLinks = wrapper.findAll("a.proof-cell");

    expect(proofLinks).toHaveLength(3);
    expect(proofLinks.map((link) => link.attributes("data-to"))).toEqual([
      '{"name":"problemset"}',
      '{"name":"contest-list"}',
      '{"name":"forum-home"}',
    ]);
  });

  it("expands one FAQ answer at a time", async () => {
    const wrapper = mountView();
    const triggers = wrapper.findAll(".faq-trigger");

    expect(triggers).toHaveLength(6);
    expect(triggers[0].attributes("aria-expanded")).toBe("true");
    await triggers[1].trigger("click");
    expect(triggers[0].attributes("aria-expanded")).toBe("false");
    expect(triggers[1].attributes("aria-expanded")).toBe("true");
    expect(wrapper.text()).toContain("landing.faq.judge.answer");
    expect(wrapper.text()).not.toContain("landing.faq.free.answer");
  });
});
