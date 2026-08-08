<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import loader from "@monaco-editor/loader";
import { registerSolarizedThemes } from "@/utils/monaco-solarized-theme";

const props = defineProps<{
  modelValue: string;
  language: string;
  theme?: "vs-dark" | "vs-light" | "hc-black";
  wordWrap?: boolean;
  minimap?: boolean;
  fontSize?: number;
  tabSize?: number;
  lineNumbers?: "on" | "off" | "relative";
  fontFamily?: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();

const container = ref<HTMLDivElement | null>(null);
let editor: import("monaco-editor").editor.IStandaloneCodeEditor | null = null;
let monacoInstance: typeof import("monaco-editor") | null = null;
let monacoPromise: Promise<typeof import("monaco-editor")> | null = null;
const globalMonacoKey = "__ULTICODE_MONACO__";

interface GlobalScope {
  [key: string]: unknown;
  monaco?: typeof import("monaco-editor");
  require?: RequireFunction;
}

interface RequireFunction {
  (
    modules: string[],
    callback: (monaco: typeof import("monaco-editor")) => void,
  ): void;
  defined?: (module: string) => boolean;
}

interface MonacoTypeScriptApi {
  typescriptDefaults: {
    setCompilerOptions(options: Record<string, unknown>): void;
    setDiagnosticsOptions(options: Record<string, unknown>): void;
  };
  javascriptDefaults: MonacoTypeScriptApi["typescriptDefaults"];
  ScriptTarget: { ESNext: number };
  ModuleResolutionKind: { NodeJs: number };
  ModuleKind: { CommonJS: number };
}

const getMonaco = async () => {
  const globalScope =
    typeof window !== "undefined" ? (window as unknown as GlobalScope) : null;
  if (monacoInstance) return monacoInstance;
  const cached =
    (globalScope?.[globalMonacoKey] as
      | typeof import("monaco-editor")
      | undefined) ??
    (globalScope?.monaco && globalScope.monaco.editor
      ? globalScope.monaco
      : null);
  if (cached) {
    monacoInstance = cached;
    return monacoInstance;
  }
  const globalRequire = globalScope?.require as RequireFunction | undefined;
  if (globalRequire?.defined?.("vs/editor/editor.main")) {
    return new Promise<typeof import("monaco-editor")>((resolve) => {
      globalRequire(
        ["vs/editor/editor.main"],
        (monaco: typeof import("monaco-editor")) => {
          monacoInstance = monaco;
          if (globalScope) {
            globalScope[globalMonacoKey] = monaco;
            globalScope.monaco = monaco;
          }
          resolve(monaco);
        },
      );
    });
  }
  if (!monacoPromise) {
    monacoPromise = loader.init().then((instance) => {
      monacoInstance = instance;
      if (globalScope) {
        globalScope[globalMonacoKey] = instance;
        globalScope.monaco = instance;
      }
      return instance;
    });
  }
  return monacoPromise;
};

const disposeEditor = () => {
  if (editor) {
    editor.dispose();
    editor = null;
  }
};

const formatDocument = async () => {
  if (!editor) return;
  const action = editor.getAction("editor.action.formatDocument");
  if (action) {
    await action.run();
  }
};

const configureLanguageFeatures = (monaco: typeof import("monaco-editor")) => {
  const typescript = monaco.languages
    .typescript as unknown as MonacoTypeScriptApi;
  // TypeScript Configuration
  // Intention Actions (Code Actions) & Inspections (Diagnostics)
  const tsDefaults = typescript.typescriptDefaults;
  tsDefaults.setCompilerOptions({
    target: typescript.ScriptTarget.ESNext,
    allowNonTsExtensions: true,
    moduleResolution: typescript.ModuleResolutionKind.NodeJs,
    module: typescript.ModuleKind.CommonJS,
    noEmit: true,
    esModuleInterop: true,
    lib: ["esnext", "dom"],
  });

  tsDefaults.setDiagnosticsOptions({
    noSemanticValidation: false,
    noSyntaxValidation: false,
  });

  // JavaScript Configuration
  // Intention Actions (Code Actions) & Inspections (Diagnostics)
  const jsDefaults = typescript.javascriptDefaults;
  jsDefaults.setCompilerOptions({
    target: typescript.ScriptTarget.ESNext,
    allowNonTsExtensions: true,
    moduleResolution: typescript.ModuleResolutionKind.NodeJs,
    module: typescript.ModuleKind.CommonJS,
    noEmit: true,
    esModuleInterop: true,
    checkJs: true,
    lib: ["esnext", "dom"],
  });

  jsDefaults.setDiagnosticsOptions({
    noSemanticValidation: false,
    noSyntaxValidation: false,
  });
};

defineExpose({
  formatDocument,
});

onMounted(async () => {
  if (typeof window === "undefined" || !container.value) return;

  const monaco = await getMonaco();
  if (!monaco) return;

  // Register Solarized themes to match project design system
  registerSolarizedThemes(monaco);

  // Configure Language Features (Inspections & Intentions)
  configureLanguageFeatures(monaco);

  editor = monaco.editor.create(container.value, {
    value: props.modelValue,
    language: props.language,
    automaticLayout: true,
    minimap: { enabled: Boolean(props.minimap) },
    fontSize: props.fontSize ?? 14,
    tabSize: props.tabSize ?? 2,
    lineNumbers: props.lineNumbers ?? "on",
    wordWrap: props.wordWrap ? "on" : "off",
    theme: props.theme ?? "vs-dark",
    fontFamily:
      props.fontFamily ??
      '"LXGW WenKai", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace',
    fontLigatures: true,
    // IntelliSense and Suggestion Options
    quickSuggestions: {
      other: true,
      comments: true,
      strings: true,
    },
    suggestSelection: "recentlyUsed",
    parameterHints: {
      enabled: true,
    },
    suggestOnTriggerCharacters: true,
    acceptSuggestionOnEnter: "on",
    tabCompletion: "on",
    folding: true,
    formatOnPaste: true,
    formatOnType: true,
    // Enable Intention Actions (Code Actions)
    lightbulb: {
      enabled: monaco.editor.ShowLightbulbIconMode.On,
    },
  });

  editor.onDidChangeModelContent(() => {
    if (!editor) return;
    const nextValue = editor.getValue();
    emit("update:modelValue", nextValue);
  });
});

watch(
  () => props.modelValue,
  (value) => {
    if (!editor) return;
    const current = editor.getValue();
    if (value !== current) {
      editor.setValue(value);
    }
  },
);

watch(
  () => props.language,
  async (language) => {
    if (!editor) return;
    const monaco = await getMonaco();
    if (!monaco) return;
    const model = editor.getModel();
    if (model) {
      monaco.editor.setModelLanguage(model, language);
    }
  },
);

watch(
  () => props.theme,
  async (theme) => {
    if (!editor || !theme) return;
    const monaco = await getMonaco();
    if (!monaco) return;
    monaco.editor.setTheme(theme);
  },
);

watch(
  () => props.wordWrap,
  (value) => {
    if (!editor) return;
    editor.updateOptions({ wordWrap: value ? "on" : "off" });
  },
);

watch(
  () => props.minimap,
  (value) => {
    if (!editor) return;
    editor.updateOptions({ minimap: { enabled: Boolean(value) } });
  },
);

watch(
  () => props.fontSize,
  (value) => {
    if (!editor || value === undefined) return;
    editor.updateOptions({ fontSize: value });
  },
);

watch(
  () => props.tabSize,
  (value) => {
    if (!editor || value === undefined) return;
    editor.updateOptions({ tabSize: value });
  },
);

watch(
  () => props.lineNumbers,
  (value) => {
    if (!editor || value === undefined) return;
    editor.updateOptions({ lineNumbers: value });
  },
);

watch(
  () => props.fontFamily,
  (value) => {
    if (!editor || value === undefined) return;
    editor.updateOptions({ fontFamily: value });
  },
);

onBeforeUnmount(() => {
  disposeEditor();
});
</script>

<template>
  <div ref="container" class="h-full w-full" />
</template>
