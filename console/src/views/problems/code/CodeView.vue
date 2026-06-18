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
      <div
        data-testid="editor-command-bar"
        class="flex flex-wrap items-center justify-between gap-2 border border-border/60 bg-card/70 p-1"
      >
        <div class="flex items-center gap-1.5">
          <DropdownMenu>
            <DropdownMenuTrigger
              class="flex h-7 cursor-pointer items-center gap-1.5 rounded-none border border-border bg-muted/60 px-3 py-1 text-xs font-semibold text-foreground outline-none transition-colors hover:bg-accent hover:text-accent-foreground data-[state=open]:border-ring/60 data-[state=open]:bg-accent data-[state=open]:text-accent-foreground"
              :aria-label="t('problem.layout.selectLanguage')"
            >
              <span class="text-xxs uppercase tracking-wider font-bold">{{
                activeLanguageLabel
              }}</span>
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

        <div class="flex h-full items-center gap-1 text-muted-foreground">
          <!-- Group 1: Code Actions -->
          <div
            class="flex items-center border border-border/60 bg-muted/60 p-0.5"
          >
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 rounded-none text-foreground hover:bg-accent hover:text-accent-foreground"
              :aria-label="t('problem.layout.formatCode')"
              :title="t('problem.layout.formatCode')"
              @click="handleFormat"
            >
              <Wand2 class="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 rounded-none text-foreground hover:bg-accent hover:text-accent-foreground disabled:opacity-30"
              :disabled="!canReset"
              :aria-label="t('problem.layout.resetCode')"
              :title="t('problem.layout.resetCode')"
              @click="handleReset"
            >
              <RotateCcw class="h-3.5 w-3.5" aria-hidden="true" />
            </Button>
          </div>

          <div class="h-4 w-px bg-border/70" />

          <!-- Group 2: Editor View Settings -->
          <div
            class="flex items-center gap-0.5 border border-border/60 bg-muted/60 p-0.5"
          >
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 rounded-none text-foreground hover:bg-accent hover:text-accent-foreground"
              :class="{
                'bg-accent text-accent-foreground': editorWordWrap,
              }"
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
              class="h-6 w-6 rounded-none text-foreground hover:bg-accent hover:text-accent-foreground"
              :class="{
                'bg-accent text-accent-foreground': editorMinimap,
              }"
              :aria-pressed="editorMinimap"
              :aria-label="t('problem.layout.toggleMinimap')"
              :title="t('problem.layout.toggleMinimap')"
              @click="toggleMinimap"
            >
              <Scan class="h-3.5 w-3.5" aria-hidden="true" />
            </Button>

            <div class="mx-0.5 h-3.5 w-px bg-border/70" />

            <!-- Editor Settings Toolbar -->
            <EditorToolbar
              :language="editorLanguage"
              @insert-template="handleInsertTemplate"
            />
          </div>

          <div class="h-4 w-px bg-border/70" />

          <!-- Group 3: Layout Window Actions -->
          <div
            class="flex items-center border border-border/60 bg-muted/60 p-0.5"
          >
            <Button
              variant="ghost"
              size="icon"
              class="h-6 w-6 rounded-none text-foreground hover:bg-accent hover:text-accent-foreground"
              :class="{
                'bg-accent text-accent-foreground': isFullscreen,
              }"
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
