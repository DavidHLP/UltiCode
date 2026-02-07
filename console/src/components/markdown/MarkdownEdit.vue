<template>
  <div
    class="flex flex-col rounded-lg border bg-card overflow-hidden h-full w-full"
  >
    <div
      v-if="!hideHeader"
      class="flex items-center border-b bg-muted/30 px-3 py-2 text-xs font-medium text-muted-foreground"
    >
      {{ t("markdown.editor") }}
    </div>
    <div
      ref="editorRef"
      class="flex-1 w-full h-full min-h-[300px]"
      :class="editorClass"
    ></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from "vue";
import * as monaco from "monaco-editor";
import loader from "@monaco-editor/loader";
import { usePreferredDark } from "@vueuse/core";
import { configureMonacoWorkers } from "@/utils/monaco-workers";
import { useI18n } from "vue-i18n";

// 确保 Worker 配置生效
configureMonacoWorkers();

import type { MarkdownEditProps } from "./type";

// ...

const props = withDefaults(defineProps<MarkdownEditProps>(), {
  modelValue: "",
  defaultValue: "",
  hideHeader: false,
  readOnly: false,
  editorClass: "",
});

const { t } = useI18n();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "change", value: string): void;
}>();

const editorRef = ref<HTMLElement | null>(null);
let editorInstance: monaco.editor.IStandaloneCodeEditor | null = null;
const isDark = usePreferredDark();

// 初始化编辑器
const initEditor = async () => {
  if (!editorRef.value) return;

  loader.config({ monaco });
  const monacoInstance = await loader.init();

  const initialValue = props.modelValue || props.defaultValue || "";

  editorInstance = monacoInstance.editor.create(editorRef.value, {
    value: initialValue,
    language: "markdown",
    theme: isDark.value ? "vs-dark" : "vs",
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    fontSize: 14,
    fontFamily: "Menlo, Monaco, 'Courier New', monospace",
    readOnly: props.readOnly,
    padding: { top: 16, bottom: 16 },
    wordWrap: "on",
  });

  // 监听内容变化
  editorInstance.onDidChangeModelContent(() => {
    const value = editorInstance?.getValue() || "";
    if (value !== props.modelValue) {
      emit("update:modelValue", value);
      emit("change", value);
    }
  });
};

// 监听 defaultValue 变化 (动态模版)
watch(
  () => props.defaultValue,
  (newVal) => {
    if (!editorInstance) return;
    const currentValue = editorInstance.getValue();
    // 如果当前内容为空，或者你需要更激进的覆盖策略（通常只在为空时填充）
    if (!currentValue || !currentValue.trim()) {
      editorInstance.setValue(newVal);
      // 同时更新 v-model
      emit("update:modelValue", newVal);
    }
  },
  { immediate: true },
);

// 监听 modelValue 变化（外部更新）
watch(
  () => props.modelValue,
  (newVal) => {
    if (editorInstance && editorInstance.getValue() !== newVal) {
      editorInstance.setValue(newVal);
    }
  },
);

// 监听主题变化
watch(isDark, (newVal) => {
  if (editorInstance) {
    monaco.editor.setTheme(newVal ? "vs-dark" : "vs");
  }
});

onMounted(() => {
  initEditor();
});

onBeforeUnmount(() => {
  if (editorInstance) {
    editorInstance.dispose();
  }
});
</script>
