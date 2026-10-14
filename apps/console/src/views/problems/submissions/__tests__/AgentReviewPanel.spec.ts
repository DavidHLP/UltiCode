import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, h, ref } from "vue";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  getCapabilities: vi.fn(),
  getLearningPlan: vi.fn(),
  createThread: vi.fn(),
  analyze: vi.fn(),
  replace: vi.fn(),
  push: vi.fn(),
  workflow: null as object | null,
  route: { query: {} as Record<string, string> },
}));

vi.mock("@/api/agent", () => ({
  agentApi: {
    getCapabilities: mocks.getCapabilities,
    getLearningPlan: mocks.getLearningPlan,
  },
}));
vi.mock("@/composables/useAgentWorkflow", async () => {
  const vue = await import("vue");
  return { useAgentWorkflow: () => mocks.workflow ?? {
    thread: vue.ref(null), events: vue.ref([]), generatedText: vue.ref(""), draftTitle: vue.ref(""), draftContent: vue.ref(""), error: vue.ref(""),
    busy: vue.ref(false), loading: vue.ref(false), analyzing: vue.ref(false), canceling: vue.ref(false), saving: vue.ref(false),
    createThread: vi.fn(), analyze: vi.fn(), loadThread: vi.fn(), clear: vi.fn(() => true),
  } };
});
vi.mock("@/stores/auth", () => ({ useAuthStore: () => ({ fetchCurrentUserId: () => "user-1" }) }));
vi.mock("vue-router", () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ replace: mocks.replace, push: mocks.push }),
}));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));
vi.mock("@/components/ui/button", () => ({
  Button: defineComponent({ inheritAttrs: true, setup(_, { slots, attrs }) { return () => h("button", attrs, slots.default?.()); } }),
}));

const row = {
  id: "submission-1", problem_id: 1, status: "Accepted", language: "java", runtime: 1, memory: 1,
  created_at: "2026-01-01T00:00:00Z", contestSubmission: false, user: { id: "user-1", username: "learner" },
};

async function loadPanel() {
  vi.resetModules();
  return (await import("../components/AgentReviewPanel.vue")).default;
}

describe("AgentReviewPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.route.query = {};
    mocks.workflow = null;
  });
  afterEach(() => vi.unstubAllEnvs());

  it("does not mount or request capabilities when the feature flag is off", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "false");
    const Panel = await loadPanel();
    const wrapper = mount(Panel, { props: { submission: row } });
    await flushPromises();
    expect(wrapper.find("section").exists()).toBe(false);
    expect(mocks.getCapabilities).not.toHaveBeenCalled();
  });

  it("opens the persistent workspace for the eligible submission", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "true");
    const Panel = await loadPanel();
    const wrapper = mount(Panel, { props: { submission: row } });
    await wrapper.find("[data-agent-workspace-entry]").trigger("click");
    expect(mocks.push).toHaveBeenCalledWith({
      name: "agent-workspace",
      query: { submissionId: row.id },
    });
    expect(mocks.getCapabilities).not.toHaveBeenCalled();
  });

  it("shows a successful typed saved-plan readback without an error notice", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "true");
    const threadId = "12345678-1234-1234-1234-123456789abc";
    mocks.route.query = { threadId };
    mocks.getCapabilities.mockResolvedValue({ analysisAvailable: true, persistenceAvailable: true, sourceMode: "metadata" });
    const saved = {
      threadId, sourceSubmissionId: row.id, status: "saved", receipt: { planId: "plan-1", cancelRequested: false, failureReason: null },
      draft: { draftVersion: 2, title: "Plan", content: "body", facts: [], hypotheses: [], citations: [], citationChecks: [], sampleScope: null },
    };
    const workflow = {
      thread: ref(null), events: ref([]), generatedText: ref(""), draftTitle: ref(""), draftContent: ref(""), error: ref(""),
      busy: ref(false), loading: ref(false), analyzing: ref(false), canceling: ref(false), saving: ref(false),
      createThread: vi.fn(), analyze: vi.fn(), loadThread: vi.fn(async () => { workflow.thread.value = saved; return saved; }), clear: vi.fn(() => true),
    };
    mocks.workflow = workflow;
    mocks.getLearningPlan.mockResolvedValue({ id: "plan-1", sourceSubmissionId: row.id, draftVersion: 2, title: "Plan", content: ["<script>alert(1)</script>", "[bad](javascript:alert(1))"].join("\n\n"), createdAt: "2026-01-01T00:00:00Z" });
    const Panel = await loadPanel();
    const wrapper = mount(Panel, { props: { submission: row } });
    await flushPromises();
    expect(mocks.getLearningPlan).toHaveBeenCalledOnce();
    expect(wrapper.text()).toContain("Plan");
    expect(wrapper.find(".prose").html()).not.toContain("<script");
    expect(wrapper.find(".prose").find("a").exists()).toBe(false);
    expect(wrapper.text()).not.toContain("agent.planReadbackUnavailable");
  });

  it("keeps recovery of an existing thread available when new analysis is disabled", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "true");
    const threadId = "12345678-1234-1234-1234-123456789abc";
    mocks.route.query = { threadId };
    mocks.getCapabilities.mockResolvedValue({ analysisAvailable: false, persistenceAvailable: true, sourceMode: "metadata" });
    const restored = {
      threadId, sourceSubmissionId: row.id, status: "unknown", receipt: { planId: null, cancelRequested: false, failureReason: null },
      draft: { draftVersion: 1, title: "Plan", content: "body", facts: [], hypotheses: [], citations: [], citationChecks: [], sampleScope: null },
    };
    const workflow = {
      thread: ref(null), events: ref([]), generatedText: ref(""), draftTitle: ref(""), draftContent: ref(""), error: ref(""),
      busy: ref(false), loading: ref(false), analyzing: ref(false), canceling: ref(false), saving: ref(false),
      createThread: vi.fn(), analyze: vi.fn(), recover: vi.fn(), loadThread: vi.fn(async () => { workflow.thread.value = restored; return restored; }), clear: vi.fn(() => true),
    };
    mocks.workflow = workflow;
    const Panel = await loadPanel();
    const wrapper = mount(Panel, { props: { submission: row } });
    await flushPromises();
    expect(wrapper.text()).toContain("agent.analysisUnavailable");
    expect(wrapper.findAll("button").some((button) => button.text() === "agent.recover")).toBe(true);
    expect(wrapper.findAll("button").some((button) => button.text() === "agent.start")).toBe(false);
    await wrapper.findAll("button").find((button) => button.text() === "agent.recover")?.trigger("click");
    expect(workflow.recover).toHaveBeenCalledOnce();
  });

  it("keeps analysis available while persistence is disabled", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "true");
    mocks.getCapabilities.mockResolvedValue({ analysisAvailable: true, persistenceAvailable: false, sourceMode: "metadata" });
    const Panel = await loadPanel();
    const wrapper = mount(Panel, { props: { submission: row } });
    await wrapper.find("button").trigger("click");
    await flushPromises();
    expect(wrapper.find("textarea").exists()).toBe(true);
    expect(wrapper.text()).toContain("agent.persistenceUnavailable");
    expect(wrapper.findAll("button").some((button) => button.text() === "agent.start")).toBe(true);
  });

  it("preserves locally edited draft text across collapse and reopen", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "true");
    mocks.getCapabilities.mockResolvedValue({ analysisAvailable: true, persistenceAvailable: true, sourceMode: "metadata" });
    const draft = { draftVersion: 1, title: "Original", content: ["<script>alert(1)</script>", "[bad](javascript:alert(1))"].join("\n\n"), facts: [], hypotheses: [], citations: [], citationChecks: [], sampleScope: null };
    const workflow = {
      thread: ref({ threadId: "thread-1", sourceSubmissionId: row.id, status: "awaiting_confirmation", draft, receipt: { planId: null, cancelRequested: false, failureReason: null } }),
      events: ref([]), generatedText: ref("analysis"), draftTitle: ref("Original"), draftContent: ref(draft.content), error: ref(""),
      busy: ref(false), loading: ref(false), analyzing: ref(false), canceling: ref(false), saving: ref(false),
      createThread: vi.fn(), analyze: vi.fn(), loadThread: vi.fn(), clear: vi.fn(() => true),
    };
    mocks.workflow = workflow;
    const Panel = await loadPanel();
    const wrapper = mount(Panel, { props: { submission: row } });
    await wrapper.find("button").trigger("click");
    await flushPromises();
    expect(wrapper.find(".prose").html()).not.toContain("<script");
    expect(wrapper.find(".prose").find("a").exists()).toBe(false);
    await wrapper.find("#agent-draft-title").setValue("Local edits");
    await wrapper.findAll("button").find((button) => button.text() === "agent.closePanel")?.trigger("click");
    expect(workflow.draftTitle.value).toBe("Local edits");
    await wrapper.find("button").trigger("click");
    await flushPromises();
    expect(workflow.draftTitle.value).toBe("Local edits");
    expect(workflow.loadThread).not.toHaveBeenCalled();
    expect(mocks.getCapabilities).toHaveBeenCalledOnce();
  });

  it("requires explicit source consent before starting an owner-source review", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "true");
    mocks.getCapabilities.mockResolvedValue({ analysisAvailable: true, persistenceAvailable: true, sourceMode: "owner_source" });
    const thread = { threadId: "thread-1", sourceSubmissionId: row.id };
    const workflow = {
      thread: ref(null), events: ref([]), generatedText: ref(""), draftTitle: ref(""), draftContent: ref(""), error: ref(""),
      busy: ref(false), loading: ref(false), analyzing: ref(false), canceling: ref(false), saving: ref(false),
      createThread: vi.fn().mockResolvedValue(thread), analyze: vi.fn().mockResolvedValue(undefined), loadThread: vi.fn(), clear: vi.fn(() => true),
    };
    mocks.workflow = workflow;
    const Panel = await loadPanel();
    const wrapper = mount(Panel, { props: { submission: row } });
    await wrapper.find("button").trigger("click");
    await flushPromises();
    const start = wrapper.findAll("button").find((button) => button.text() === "agent.start");
    expect(start?.attributes("disabled")).toBeDefined();
    await wrapper.find('input[type="checkbox"]').setValue(true);
    const enabledStart = wrapper.findAll("button").find((button) => button.text() === "agent.start");
    expect(enabledStart?.attributes("disabled")).toBeUndefined();
    await enabledStart?.trigger("click");
    await flushPromises();
    expect(workflow.createThread).toHaveBeenCalledWith(row.id, "agent.promptComplexity");
    expect(workflow.analyze).toHaveBeenCalledOnce();
  });

  it("blocks starting a new thread while analysis is active", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "true");
    mocks.route.query = { threadId: "12345678-1234-1234-1234-123456789abc" };
    mocks.getCapabilities.mockResolvedValue({ analysisAvailable: true, persistenceAvailable: true, sourceMode: "metadata" });
    const workflow = {
      thread: ref({
        threadId: "thread-1", sourceSubmissionId: row.id, status: "analyzing",
        draft: { title: "Plan", content: "body", facts: [], hypotheses: [], citations: [], citationChecks: [] },
        receipt: { planId: null, cancelRequested: false, failureReason: null },
      }),
      events: ref([]), generatedText: ref(""), draftTitle: ref("Plan"), draftContent: ref("body"), error: ref(""),
      busy: ref(true), loading: ref(false), analyzing: ref(true), canceling: ref(false), saving: ref(false),
      createThread: vi.fn(), analyze: vi.fn(), loadThread: vi.fn(), clear: vi.fn(() => true),
    };
    mocks.workflow = workflow;
    const Panel = await loadPanel();
    const wrapper = mount(Panel, { props: { submission: row } });
    await flushPromises();
    const initialClearCount = workflow.clear.mock.calls.length;

    const newThread = wrapper.findAll("button").find((button) => button.text() === "agent.newThread");
    expect(newThread?.attributes("disabled")).toBeDefined();
    await newThread?.trigger("click");
    expect(workflow.clear).toHaveBeenCalledTimes(initialClearCount);
  });

  it("confirms before discarding an unsaved review when starting a new thread", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "true");
    mocks.route.query = { threadId: "12345678-1234-1234-1234-123456789abc" };
    mocks.getCapabilities.mockResolvedValue({ analysisAvailable: true, persistenceAvailable: true, sourceMode: "metadata" });
    const workflow = {
      thread: ref({
        threadId: "thread-1", sourceSubmissionId: row.id, status: "awaiting_confirmation",
        draft: { title: "Plan", content: "body", facts: [], hypotheses: [], citations: [], citationChecks: [] },
        receipt: { planId: null, cancelRequested: false, failureReason: null },
      }),
      events: ref([]), generatedText: ref("Review answer"), draftTitle: ref("Plan"), draftContent: ref("body"), error: ref(""),
      busy: ref(false), loading: ref(false), analyzing: ref(false), canceling: ref(false), saving: ref(false),
      createThread: vi.fn(), analyze: vi.fn(), loadThread: vi.fn(), clear: vi.fn(() => true),
    };
    mocks.workflow = workflow;
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    const Panel = await loadPanel();
    const wrapper = mount(Panel, { props: { submission: row } });
    await flushPromises();
    const initialClearCount = workflow.clear.mock.calls.length;

    const newThread = wrapper.findAll("button").find((button) => button.text() === "agent.newThread");
    await newThread?.trigger("click");
    expect(confirm).toHaveBeenCalledOnce();
    expect(confirm).toHaveBeenCalledWith("agent.newThreadConfirm");
    expect(workflow.clear).toHaveBeenCalledTimes(initialClearCount);

    confirm.mockReturnValue(true);
    await newThread?.trigger("click");
    expect(workflow.clear).toHaveBeenCalledTimes(initialClearCount + 1);
  });

  it("confirms before replacing an unsaved review when reloading its thread", async () => {
    vi.stubEnv("VITE_AGENT_REVIEW_ENABLED", "true");
    const threadId = "12345678-1234-1234-1234-123456789abc";
    mocks.route.query = { threadId };
    mocks.getCapabilities.mockResolvedValue({ analysisAvailable: true, persistenceAvailable: true, sourceMode: "metadata" });
    const workflow = {
      thread: ref({
        threadId, sourceSubmissionId: row.id, status: "awaiting_confirmation",
        draft: { title: "Plan", content: "body", facts: [], hypotheses: [], citations: [], citationChecks: [] },
        receipt: { planId: null, cancelRequested: false, failureReason: null },
      }),
      events: ref([]), generatedText: ref("Review answer"), draftTitle: ref("Plan"), draftContent: ref("body"), error: ref(""),
      busy: ref(false), loading: ref(false), analyzing: ref(false), canceling: ref(false), saving: ref(false),
      createThread: vi.fn(), analyze: vi.fn(), loadThread: vi.fn(), clear: vi.fn(() => true),
    };
    mocks.workflow = workflow;
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    const Panel = await loadPanel();
    const wrapper = mount(Panel, { props: { submission: row } });
    await flushPromises();
    const initialLoadCount = workflow.loadThread.mock.calls.length;

    const refresh = wrapper.findAll("button").find((button) => button.text() === "agent.refresh");
    await refresh?.trigger("click");
    expect(confirm).toHaveBeenCalledOnce();
    expect(confirm).toHaveBeenCalledWith("agent.reloadConfirm");
    expect(workflow.loadThread).toHaveBeenCalledTimes(initialLoadCount);

    confirm.mockReturnValue(true);
    await refresh?.trigger("click");
    expect(workflow.loadThread).toHaveBeenCalledTimes(initialLoadCount + 1);
  });
});
