<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { computed, onBeforeUnmount, ref, inject } from "vue";
import { Play, CloudUpload, StickyNote } from "lucide-vue-next";
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card";
import { Kbd, KbdGroup } from "@/components/ui/kbd";
import { useBottomPanelStore } from "../test/test";
import { useHeaderStore } from "@/stores/headerStore";
import { storeToRefs } from "pinia";
import { createSubmission } from "@/api/submission";
import { submitContestProblem } from "@/api/contest";
import { toast } from "vue-sonner";
import { ToggleNotesKey } from "../problem-context";
import { useProblemContext } from "../useProblemContext";
import { useProblemEditorStore } from "@/stores/problemEditorStore";
import { useI18n } from "vue-i18n";

const { requestRun } = useBottomPanelStore();
const headerStore = useHeaderStore();
const problemContext = useProblemContext();
const contestId = computed(() => problemContext.contestId.value);
const toggleNotes = inject(ToggleNotesKey, () => {});
const editorStore = useProblemEditorStore();
const { code, language } = storeToRefs(editorStore);
const { t } = useI18n();

const isRunning = ref(false);
const isSubmitting = ref(false);
const runPulseKey = ref(0);
const runTimer = ref<ReturnType<typeof setTimeout> | null>(null);

const handleRun = () => {
  requestRun();
  headerStore.setActiveGroup("test-info");
  headerStore.setActiveHeader("test-info", 6);
  runPulseKey.value = Date.now();
  isRunning.value = true;
  if (runTimer.value) {
    clearTimeout(runTimer.value);
  }
  runTimer.value = setTimeout(() => {
    isRunning.value = false;
  }, 1200);
};

async function handleSubmit() {
  const prob = problemContext.problem.value;
  if (!prob) return;

  const currentCode = code.value;
  const currentLanguage = language.value || "typescript";
  if (!currentCode.trim()) {
    toast.error(t("problem.messages.enterTitle")); // Or suitable key
    return;
  }

  isSubmitting.value = true;
  try {
    const res = contestId.value
      ? await submitContestProblem(contestId.value, prob.id, {
          language: currentLanguage,
          code: currentCode,
        })
      : await createSubmission(prob.id, {
          language: currentLanguage,
          code: currentCode,
        });
    toast.success(`${t("problem.editor.submit")} ${res.status}!`);
    // Navigate to submissions tab
    headerStore.setActiveGroup("problem-info");
    headerStore.setActiveHeader("problem-info", 4);
  } catch (e) {
    toast.error(t("problem.problemList.messages.saveFailed"));
    console.error(e);
  } finally {
    isSubmitting.value = false;
  }
}

onBeforeUnmount(() => {
  if (runTimer.value) {
    clearTimeout(runTimer.value);
  }
});
</script>

<template>
  <div
    class="relative z-20 flex min-w-60 flex-1 items-center overflow-hidden pointer-events-auto"
  >
    <div class="flex items-center overflow-hidden rounded focus:outline-none">
      <div class="relative group/nav-back flex items-center">
        <!-- Run button with HoverCard -->
        <HoverCard :open-delay="200">
          <HoverCardTrigger as-child>
            <Button
              variant="ghost"
              size="icon"
              :aria-label="t('problem.layout.runCode')"
              :aria-busy="isRunning"
              class="group relative flex-none cursor-pointer flex items-center h-8 transition hover:bg-primary/10 text-gray-600 w-9 focus:outline-none focus:ring-0 focus:ring-offset-0 bg-gray-100 overflow-hidden rounded-md"
              @click="handleRun"
            >
              <span
                v-if="isRunning"
                :key="runPulseKey"
                class="pointer-events-none absolute inset-0 animate-[ping_1s_ease-out] rounded-md bg-primary/20"
              />
              <span
                class="pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-200 group-hover:opacity-50"
                :class="isRunning ? 'bg-primary/15' : 'bg-primary/5'"
              />
              <Play
                class="h-4 w-4 transition-transform duration-200"
                :class="
                  isRunning
                    ? 'text-primary animate-[spin_0.9s_linear]'
                    : 'text-gray-700'
                "
              />
            </Button>
          </HoverCardTrigger>
          <HoverCardContent class="h-auto w-auto p-2">
            <div class="flex items-center gap-1">
              <p class="text-xs leading-none">
                {{ t("problem.layout.runCode") }}
              </p>
              <KbdGroup class="text-xs">
                <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs">F5</Kbd>
              </KbdGroup>
            </div>
          </HoverCardContent>
        </HoverCard>

        <Separator
          orientation="vertical"
          class="h-7 w-px flex-none bg-gray-200"
        />

        <!-- Submit button -->
        <HoverCard :open-delay="200">
          <HoverCardTrigger as-child>
            <Button
              variant="ghost"
              :aria-label="t('problem.layout.submitSolution')"
              :disabled="isSubmitting"
              class="group cursor-pointer gap-2 overflow-hidden hover:text-lc-icon-primary flex items-center h-8 transition-none hover:bg-gray-200 text-gray-60 px-2 bg-gray-200 disabled:opacity-50"
              @click="handleSubmit"
            >
              <CloudUpload
                class="h-4 w-4"
                :class="isSubmitting && 'animate-bounce'"
              />
              <div class="relative flex items-center gap-1 overflow-hidden">
                <div
                  class="truncate font-medium group-hover:text-lc-text-primary text-text-primary hover:text-text-primary"
                >
                  {{
                    isSubmitting
                      ? t("problem.layout.formatting")
                      : t("problem.layout.submitSolution")
                  }}
                </div>
              </div>
            </Button>
          </HoverCardTrigger>
          <HoverCardContent class="h-auto w-auto p-2">
            <div class="flex items-center gap-1">
              <p class="text-xs leading-none">
                {{ t("problem.layout.submitSolution") }}
              </p>
              <KbdGroup class="text-xs">
                <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs">Ctrl</Kbd>
                <span class="text-xs">+</span>
                <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs">Enter</Kbd>
              </KbdGroup>
            </div>
          </HoverCardContent>
        </HoverCard>

        <!-- Notes button -->
        <HoverCard :open-delay="200">
          <HoverCardTrigger as-child>
            <Button
              variant="ghost"
              size="icon"
              :aria-label="t('problem.detail.notes')"
              class="group flex-none cursor-pointer flex items-center h-8 transition-none hover:bg-gray-200 text-gray-600 w-8 focus:outline-none focus:ring-0 focus:ring-offset-0 bg-gray-200"
              @click="toggleNotes"
            >
              <StickyNote class="h-4 w-4" />
            </Button>
          </HoverCardTrigger>
          <HoverCardContent class="h-auto w-auto p-2">
            <div class="flex items-center gap-1">
              <p class="text-xs leading-none">
                {{ t("problem.detail.notes") }}
              </p>
              <KbdGroup class="text-xs">
                <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs">Ctrl</Kbd>
                <span class="text-xs">+</span>
                <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs">N</Kbd>
              </KbdGroup>
            </div>
          </HoverCardContent>
        </HoverCard>
      </div>
    </div>
  </div>
</template>
