<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { usePreferredDark } from "@vueuse/core";
import { useCodeCache } from "@/composables/useCodeCache";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import CodeEditor from "./components/CodeEditor.vue";
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
import { useI18n } from "vue-i18n";

const props = defineProps<{
  languages: ProblemLanguageOption[];
  starterNotes: string[];
}>();

const { t } = useI18n();
const prefersDark = usePreferredDark();
const editorStore = useProblemEditorStore();

const activeLanguageValue = ref(props.languages[0]?.value ?? "");
const code = ref("");
const editorContainer = ref<HTMLElement | null>(null);
const editorRef = ref<InstanceType<typeof CodeEditor> | null>(null);
const isWordWrapEnabled = ref(false);
const isMinimapVisible = ref(false);
const isFullscreen = ref(false);

const languageMeta = computed(() =>
  props.languages.find((lang) => lang.value === activeLanguageValue.value),
);

const editorLanguage = computed(
  () => languageMeta.value?.value ?? "typescript",
);
const editorTheme = computed(() =>
  prefersDark.value ? "vs-dark" : "vs-light",
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
  isWordWrapEnabled.value = !isWordWrapEnabled.value;
};

const toggleMinimap = () => {
  isMinimapVisible.value = !isMinimapVisible.value;
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
      editorStore.setLanguage(value);
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
    editorStore.setCode(value);
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
      <div class="flex flex-wrap items-center justify-between gap-1">
        <div class="flex items-center gap-1">
          <DropdownMenu>
            <DropdownMenuTrigger
              class="h-8 px-3 py-1 text-xs font-medium flex items-center gap-1 hover:bg-muted rounded-md transition-colors outline-none"
              :aria-label="t('problem.layout.selectLanguage')"
            >
              {{ activeLanguageLabel }}
              <ChevronDown class="h-3 w-3 opacity-50" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" class="w-[200px]">
              <DropdownMenuItem
                v-for="language in props.languages"
                :key="language.id"
                @select="activeLanguageValue = language.value"
                class="text-xs cursor-pointer"
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
          <Button
            variant="ghost"
            size="icon"
            class="h-7 w-7"
            :aria-pressed="isWordWrapEnabled"
            :title="t('problem.layout.toggleWordWrap')"
            @click="toggleWordWrap"
          >
            <AlignLeft class="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            class="h-7 w-7"
            :aria-label="t('problem.layout.formatCode')"
            :title="t('problem.layout.formatCode')"
            @click="handleFormat"
          >
            <Wand2 class="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            class="h-7 w-7"
            :disabled="!canReset"
            :aria-label="t('problem.layout.resetCode')"
            :title="t('problem.layout.resetCode')"
            @click="handleReset"
          >
            <RotateCcw class="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            class="h-7 w-7"
            :aria-pressed="isFullscreen"
            :title="t('problem.layout.toggleFullscreen')"
            @click="handleFullscreenToggle"
          >
            <Maximize2 class="h-3.5 w-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            class="h-7 w-7"
            :aria-pressed="isMinimapVisible"
            :title="t('problem.layout.toggleMinimap')"
            @click="toggleMinimap"
          >
            <Scan class="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>

      <CodeEditor
        ref="editorRef"
        v-model="code"
        :language="editorLanguage"
        :theme="editorTheme"
        :word-wrap="isWordWrapEnabled"
        :minimap="isMinimapVisible"
        class="flex-1 min-h-0"
      />
    </main>
  </div>
</template>
