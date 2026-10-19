import { shallowMount } from "@vue/test-utils";
import { nextTick, ref } from "vue";
import { expect, it, vi } from "vitest";
import ForumFeedbackView from "./ForumFeedbackView.vue";

const { toast } = vi.hoisted(() => ({
  toast: { error: vi.fn(), info: vi.fn(), success: vi.fn() },
}));
vi.mock("vue-sonner", () => ({ toast }));

const locale = ref("en-US");
vi.mock("vue-i18n", async (original) => ({
  ...(await original<typeof import("vue-i18n")>()),
  useI18n: () => ({ t: (key: string) => `${locale.value}:${key}` }),
}));

it("updates the selected feedback type when the language changes", async () => {
  const view = shallowMount(ForumFeedbackView, {
    global: { renderStubDefaultSlot: true, stubs: { RouterLink: true } },
  });
  try {
    const value = view.findComponent({ name: "SelectValue" });
    expect(value.text()).toBe("en-US:forum.feedback.types.bug");
    view
      .findComponent({ name: "Select" })
      .vm.$emit("update:modelValue", "improvement");
    await nextTick();
    expect(value.text()).toBe("en-US:forum.feedback.types.improvement");
    locale.value = "zh-CN";
    await nextTick();
    expect(value.text()).toBe("zh-CN:forum.feedback.types.improvement");
    expect(view.findComponent({ name: "Select" }).props("modelValue")).toBe(
      "improvement",
    );
  } finally {
    view.unmount();
    locale.value = "en-US";
  }
});

it("does not claim valid feedback was submitted when delivery is unavailable", async () => {
  vi.clearAllMocks();
  const view = shallowMount(ForumFeedbackView, {
    global: { renderStubDefaultSlot: true, stubs: { RouterLink: true } },
  });
  try {
    const subject = view.findComponent({ name: "Input" });
    const description = view.findComponent({ name: "Textarea" });
    subject.vm.$emit("update:modelValue", "Cannot submit solution");
    description.vm.$emit("update:modelValue", "I see an error after submit.");
    await view.find("form").trigger("submit");

    expect(toast.info).toHaveBeenCalledWith(
      "en-US:forum.feedback.unavailable",
    );
    expect(toast.success).not.toHaveBeenCalled();
    expect(subject.props("modelValue")).toBe("Cannot submit solution");
    expect(description.props("modelValue")).toBe(
      "I see an error after submit.",
    );
  } finally {
    view.unmount();
  }
});
