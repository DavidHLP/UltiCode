import { beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import CodeEditor from "../CodeEditor.vue";

const mocks = vi.hoisted(() => {
  const defaults = () => ({
    setCompilerOptions: vi.fn(),
    setDiagnosticsOptions: vi.fn(),
  });
  const editor = {
    dispose: vi.fn(),
    onDidChangeModelContent: vi.fn(),
  };
  const monaco = {
    typescript: {
      typescriptDefaults: defaults(),
      javascriptDefaults: defaults(),
      ScriptTarget: { ESNext: 99 },
      ModuleResolutionKind: { NodeJs: 2 },
      ModuleKind: { CommonJS: 1 },
    },
    editor: {
      create: vi.fn(() => editor),
      ShowLightbulbIconMode: { On: "on" },
    },
  };
  return { monaco, editor, disconnect: vi.fn(), workers: vi.fn() };
});

vi.mock("monaco-editor", () => mocks.monaco);
vi.mock("@monaco-editor/loader", () => ({
  default: { config: vi.fn(), init: vi.fn(async () => mocks.monaco) },
}));
vi.mock("@/utils/monaco-workers", () => ({
  configureMonacoWorkers: mocks.workers,
}));
vi.mock("@/utils/monaco-theme", () => ({
  registerAppThemes: vi.fn(),
  observeAppEditorAppearance: vi.fn(() => mocks.disconnect),
}));

describe("CodeEditor local Monaco integration", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    delete (window as unknown as Record<string, unknown>).__ULTICODE_MONACO__;
    delete (window as unknown as Record<string, unknown>).monaco;
    document.documentElement.classList.remove("dark");
    document.documentElement.style.setProperty("--uc-font-code", "monospace");
  });

  it("uses the current top-level TypeScript API and light application defaults", async () => {
    const wrapper = mount(CodeEditor, {
      props: { modelValue: "int main() {}", language: "cpp" },
    });
    await flushPromises();
    expect(mocks.workers).toHaveBeenCalledOnce();
    expect(
      mocks.monaco.typescript.typescriptDefaults.setCompilerOptions,
    ).toHaveBeenCalledWith(expect.objectContaining({ target: 99, module: 1 }));
    expect(
      mocks.monaco.typescript.javascriptDefaults.setDiagnosticsOptions,
    ).toHaveBeenCalledWith({
      noSemanticValidation: false,
      noSyntaxValidation: false,
    });
    expect(mocks.monaco.editor.create).toHaveBeenCalledWith(
      expect.any(HTMLElement),
      expect.objectContaining({
        language: "cpp",
        theme: "vs-light",
        fontFamily: "monospace",
      }),
    );
    wrapper.unmount();
    expect(mocks.editor.dispose).toHaveBeenCalledOnce();
    expect(mocks.disconnect).toHaveBeenCalledOnce();
  });

  it("does not create an editor after its async load outlives the component", async () => {
    const wrapper = mount(CodeEditor, {
      props: { modelValue: "", language: "cpp" },
    });
    wrapper.unmount();
    await flushPromises();
    expect(mocks.monaco.editor.create).not.toHaveBeenCalled();
  });
});
