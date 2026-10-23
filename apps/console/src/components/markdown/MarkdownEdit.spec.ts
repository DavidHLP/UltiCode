import { flushPromises, mount } from "@vue/test-utils";
import { expect, it, vi } from "vitest";
import MarkdownEdit from "./MarkdownEdit.vue";

const { create, setValue, dispose } = vi.hoisted(() => {
  const setValue = vi.fn();
  const dispose = vi.fn();
  return {
    setValue,
    dispose,
    create: vi.fn(() => ({
      getValue: () => "",
      setValue,
      dispose,
      onDidChangeModelContent: vi.fn(),
    })),
  };
});
vi.mock("monaco-editor", () => ({ editor: { create } }));
vi.mock("@monaco-editor/loader", () => ({
  default: { config: vi.fn(), init: async () => ({ editor: { create } }) },
}));
vi.mock("@/utils/monaco-workers", () => ({ configureMonacoWorkers: vi.fn() }));
vi.mock("@/utils/monaco-theme", () => ({
  registerAppThemes: vi.fn(),
  observeAppEditorAppearance: () => vi.fn(),
}));
vi.mock("vue-i18n", () => ({ useI18n: () => ({ t: (key: string) => key }) }));

it("wraps continuation lines without repeating deep indentation or changing Markdown", async () => {
  const value = "```cpp\n                return deeplyNestedExpression;\n```";
  const wrapper = mount(MarkdownEdit, { props: { modelValue: value } });
  try {
    await flushPromises();
    expect(create).toHaveBeenCalledWith(
      expect.any(HTMLElement),
      expect.objectContaining({
        value,
        wordWrap: "on",
        wrappingIndent: "none",
        fontSize: 14,
      }),
    );
    expect(setValue).not.toHaveBeenCalled();
    expect(wrapper.emitted("update:modelValue")).toBeUndefined();
  } finally {
    wrapper.unmount();
  }
  expect(dispose).toHaveBeenCalledOnce();
});
