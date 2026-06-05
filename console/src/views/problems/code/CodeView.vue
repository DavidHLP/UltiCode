<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useCodeCache } from "@/composables/useCodeCache";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import CodeEditor from "./components/CodeEditor.vue";
import EditorToolbar from "@/components/editor/EditorToolbar.vue";
import type { ProblemLanguageOption } from "@/types/problem-detail";
import {
  AlignLeft,
  RotateCcw,
  Maximize2,
  Scan,
  CheckIcon,
  Wand2,
  ChevronDown,
} from "lucide-vue-next";
import { problemHooks } from "@/hooks/problem-hooks";
import { useProblemEditorStore } from "@/stores/problemEditorStore";
import { useEditorSettingsStore } from "@/stores/editorSettings";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  languages: ProblemLanguageOption[];
  starterNotes: string[];
  problemKey: string;
}>();

const { t } = useI18n();
const problemEditorStore = useProblemEditorStore();
const editorSettingsStore = useEditorSettingsStore();

const activeLanguageValue = ref(props.languages[0]?.value ?? "");
const code = ref("");
const editorContainer = ref<HTMLElement | null>(null);
const editorRef = ref<InstanceType<typeof CodeEditor> | null>(null);
const isFullscreen = ref(false);

const languageMeta = computed(() =>
  props.languages.find((lang) => lang.value === activeLanguageValue.value),
);

const editorLanguage = computed(
  () => languageMeta.value?.value ?? "typescript",
);

// Use persisted settings from store
const editorTheme = computed(() => editorSettingsStore.settings.theme);
const editorFontSize = computed(() => editorSettingsStore.settings.fontSize);
const editorTabSize = computed(() => editorSettingsStore.settings.tabSize);
const editorWordWrap = computed(() => editorSettingsStore.settings.wordWrap);
const editorMinimap = computed(() => editorSettingsStore.settings.minimap);
const editorLineNumbers = computed(
  () => editorSettingsStore.settings.lineNumbers,
);
const editorFontFamily = computed(
  () => editorSettingsStore.settings.fontFamily,
);

const activeLanguageLabel = computed(() => {
  if (languageMeta.value?.style) {
    return languageMeta.value.style === "typescript"
      ? "TypeScript"
      : "JavaScript";
  }
  return languageMeta.value?.label ?? t("problem.editor.language");
});
const starterCode = computed(() => languageMeta.value?.starterCode ?? "");
const canReset = computed(() => code.value !== starterCode.value);

const toggleWordWrap = () => {
  editorSettingsStore.toggleWordWrap();
};

const toggleMinimap = () => {
  editorSettingsStore.toggleMinimap();
};

const handleInsertTemplate = (templateCode: string) => {
  code.value = templateCode;
};

const handleReset = () => {
  code.value = starterCode.value;
};

const handleFormat = async () => {
  await editorRef.value?.formatDocument?.();
};

const handleFullscreenToggle = async () => {
  if (typeof document === "undefined") return;
  if (!document.fullscreenElement) {
    await editorContainer.value?.requestFullscreen?.();
    return;
  }
  await document.exitFullscreen?.();
};

const handleFullscreenChange = () => {
  if (typeof document === "undefined") return;
  isFullscreen.value = Boolean(document.fullscreenElement);
};

onMounted(() => {
  if (typeof document === "undefined") return;
  document.addEventListener("fullscreenchange", handleFullscreenChange);
});

onBeforeUnmount(() => {
  if (typeof document === "undefined") return;
  document.removeEventListener("fullscreenchange", handleFullscreenChange);
});

const codeCache = useCodeCache();

watch(
  () => activeLanguageValue.value,
  (value, previous) => {
    // Save previous language's code (with LRU eviction if at capacity)
    if (previous && code.value) {
      codeCache.set(previous, code.value);
    }

    // Load new language's code
    const target = props.languages.find((lang) => lang.value === value);
    if (target) {
      const cached = codeCache.get(value);
      if (cached) {
        code.value = cached;
      } else {
        code.value = target.starterCode;
      }
    }
    if (value) {
      problemEditorStore.setLanguage(value);
    }
    if (previous !== undefined && value !== previous) {
      void problemHooks.emit("problem:code:language:change", {
        from: previous,
        to: value,
      });
    }
  },
  { immediate: true },
);

watch(
  () => code.value,
  (value) => {
    problemEditorStore.setCode(value);
  },
  { immediate: true },
);
</script>

<template>
  <div ref="editorContainer" class="h-full w-full flex flex-col">
    <!-- Tab Header -->
    <!-- Tab Header Removed -->

    <!-- Main Content Area -->
    <main class="flex flex-col gap-1 p-1 flex-1 min-h-0">
      <div class="flex flex-wrap items-center justify-between gap-2 border-b border-border/30 pb-2 mb-1 px-1">
        <div class="flex items-center gap-1.5">
          <DropdownMenu>
            <DropdownMenuTrigger
              class="h-7 px-3 py-1 text-xs font-semibold bg-[var(--silver-100)] dark:bg-[var(--silver-800)] border border-border flex items-center gap-1.5 hover:bg-muted rounded-none transition-colors outline-none cursor-pointer text-foreground"
              :aria-label="t('problem.layout.selectLanguage')"
            >
              <span class="text-[11px] uppercase tracking-wider font-bold">{{ activeLanguageLabel }}</span>
              <ChevronDown class="h-3 w-3 opacity-60" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" class="w-[200px] rounded-none">
              <DropdownMenuItem
                v-for="language in props.languages"
                :key="language.id"
                @select="activeLanguageValue = language.value"
                class="text-xs cursor-pointer rounded-none"
              >
                <CheckIcon
                  class="mr-2 h-3.5 w-3.5"
                  :class="
                    language.value === activeLanguageValue
                      ? 'opacity-100'
                      : 'opacity-0'
                  "
                />
                {{ language.label }}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>

        <div class="flex h-full items-center gap-2 text-muted-foreground">
          <!-- Group 1: Code Actions -->
          <div class="flex items-center bg-[var(--silver-50)] dark:bg-[var(--silver-900)]/40 p-0.5 border border-border/40">
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 hover:bg-muted rounded-none text-foreground"
              :aria-label="t('problem.layout.formatCode')"
              :title="t('problem.layout.formatCode')"
              @click="handleFormat"
            >
              <Wand2 class="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 hover:bg-muted rounded-none text-foreground disabled:opacity-30"
              :disabled="!canReset"
              :aria-label="t('problem.layout.resetCode')"
              :title="t('problem.layout.resetCode')"
              @click="handleReset"
            >
              <RotateCcw class="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          </div>

          <div class="h-4 w-px bg-border/50" />

          <!-- Group 2: Editor View Settings -->
          <div class="flex items-center bg-[var(--silver-50)] dark:bg-[var(--silver-900)]/40 p-0.5 border border-border/40 gap-0.5">
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 hover:bg-muted rounded-none text-foreground"
              :class="{ 'bg-muted': editorWordWrap }"
              :aria-pressed="editorWordWrap"
              :aria-label="t('problem.layout.toggleWordWrap')"
              :title="t('problem.layout.toggleWordWrap')"
              @click="toggleWordWrap"
            >
              <AlignLeft class="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 hover:bg-muted rounded-none text-foreground"
              :class="{ 'bg-muted': editorMinimap }"
              :aria-pressed="editorMinimap"
              :aria-label="t('problem.layout.toggleMinimap')"
              :title="t('problem.layout.toggleMinimap')"
              @click="toggleMinimap"
            >
              <Scan class="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
            
            <div class="h-3.5 w-px bg-border/40 mx-0.5" />

            <!-- Editor Settings Toolbar -->
            <EditorToolbar
              :language="editorLanguage"
              @insert-template="handleInsertTemplate"
              class="scale-90"
            />
          </div>

          <div class="h-4 w-px bg-border/50" />

          <!-- Group 3: Layout Window Actions -->
          <div class="flex items-center bg-[var(--silver-50)] dark:bg-[var(--silver-900)]/40 p-0.5 border border-border/40">
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 hover:bg-muted rounded-none text-foreground"
              :aria-pressed="isFullscreen"
              :aria-label="t('problem.layout.toggleFullscreen')"
              :title="t('problem.layout.toggleFullscreen')"
              @click="handleFullscreenToggle"
            >
              <Maximize2 class="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          </div>
        </div>
      </div>

      <CodeEditor
        ref="editorRef"
        v-model="code"
        :language="editorLanguage"
        :theme="editorTheme"
        :font-size="editorFontSize"
        :tab-size="editorTabSize"
        :word-wrap="editorWordWrap"
        :minimap="editorMinimap"
        :line-numbers="editorLineNumbers"
        :font-family="editorFontFamily"
        class="flex-1 min-h-0"
      />
    </main>
  </div>
</template>
