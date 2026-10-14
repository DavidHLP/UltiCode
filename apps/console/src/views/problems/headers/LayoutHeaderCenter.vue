<script setup lang="ts">
import { Button } from "@ulticode/design-system/ui/button";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { Play, CloudUpload } from "@lucide/vue";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@ulticode/design-system/ui/tooltip";
import { Kbd, KbdGroup } from "@ulticode/design-system/ui/kbd";
import { useBottomPanelStore } from "../test/test";
import { useHeaderStore } from "@/stores/headerStore";
import { storeToRefs } from "pinia";
import { createSubmission } from "@/api/submission";
import { submitContestProblem } from "@/api/contest";
import { toast } from "vue-sonner";
import { useProblemContext } from "../useProblemContext";
import { useProblemEditorStore } from "@/stores/problemEditorStore";
import { useAuthStore } from "@/stores/auth";
import { useContestProblemShellStore } from "@/stores/contestProblemShell";
import { useI18n } from "vue-i18n";
import { registerGlobalShortcut } from "@/composables/useGlobalShortcuts";
import KeyboardShortcutsModal from "@/components/editor/KeyboardShortcutsModal.vue";

const bottomPanelStore = useBottomPanelStore();
const { requestRun } = bottomPanelStore;
const headerStore = useHeaderStore();
const problemContext = useProblemContext();
const contestId = computed(() => problemContext.contestId.value);
const editorStore = useProblemEditorStore();
const { code, language } = storeToRefs(editorStore);
const { t } = useI18n();

const isSubmitting = ref(false);
const runPulseKey = ref(0);
const showShortcutsModal = ref(false);

const handleRun = () => {
  if (bottomPanelStore.isRunning) return;
  requestRun();
  headerStore.setActiveGroup("test-info");
  headerStore.setActiveHeader("test-info", 6);
  runPulseKey.value = Date.now();
};

async function handleSubmit() {
  if (isSubmitting.value) return;
  const prob = problemContext.problem.value;
  if (!prob) return;

  const currentCode = code.value;
  const currentLanguage = language.value || "javascript";
  if (!currentCode.trim()) {
    toast.error(t("problem.messages.codeRequired"));
    return;
  }

  const authStore = useAuthStore();
  if (!authStore.isAuthenticated) {
    toast.error(t("problem.messages.loginRequired"));
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
    // In contest mode the contest-aware toast is rendered by the
    // ContestProblemDock (it has access to score, rank, penalty).
    // The dock watches `useContestProblemShellStore().lastSubmitResult`
    // — pushing here is what triggers the toast + score refresh.
    // Outside contest mode we keep the simple "Submitted: <verdict>"
    // toast that's been there since v1.
    if (contestId.value) {
      useContestProblemShellStore().pushSubmit(res);
    } else {
      toast.success(`${t("problem.editor.submit")} ${res.status}!`);
    }
    headerStore.setActiveGroup("problem-info");
    headerStore.setActiveHeader("problem-info", 3);
  } catch (e) {
    toast.error(t("problem.problemList.messages.saveFailed"));
    console.error(e);
  } finally {
    isSubmitting.value = false;
  }
}

// Register global keyboard shortcuts
let unregisterFns: (() => void)[] = [];

onMounted(() => {
  // F5 - Run code
  unregisterFns.push(
    registerGlobalShortcut({
      key: "F5",
      handler: handleRun,
    }),
  );

  // Ctrl+Enter - Submit code
  unregisterFns.push(
    registerGlobalShortcut({
      key: "Enter",
      ctrl: true,
      handler: handleSubmit,
    }),
  );

  // Ctrl+/ - Show shortcuts
  unregisterFns.push(
    registerGlobalShortcut({
      key: "/",
      ctrl: true,
      handler: () => {
        showShortcutsModal.value = true;
      },
    }),
  );
});

onBeforeUnmount(() => {
  // Unregister all shortcuts
  unregisterFns.forEach((fn) => fn());
  unregisterFns = [];
});
</script>

<template>
  <div class="relative z-20 flex min-w-0 items-center pointer-events-auto">
    <div
      class="flex items-center overflow-hidden rounded-lg focus:outline-none"
    >
      <div class="relative group/nav-back flex items-center gap-2">
        <!-- Run button with Tooltip -->
        <Tooltip :delay-duration="200">
          <TooltipTrigger as-child>
            <Button
              :aria-label="t('problem.layout.runCode')"
              :aria-busy="bottomPanelStore.isRunning"
              :disabled="bottomPanelStore.isRunning"
              class="group flex cursor-pointer gap-1.5 items-center px-3 disabled:opacity-50 focus:outline-none"
              @click="handleRun"
            >
              <Play
                class="h-3.5 w-3.5 text-status-success-mark transition-transform duration-200"
                :class="
                  bottomPanelStore.isRunning ? 'animate-[spin_0.9s_linear]' : ''
                "
              />
              <span class="truncate">{{ t("problem.layout.runCode") }}</span>
            </Button>
          </TooltipTrigger>
          <TooltipContent class="h-auto w-auto p-2">
            <div class="flex items-center gap-1">
              <p class="text-xs leading-none">
                {{ t("problem.layout.runCode") }}
              </p>
              <KbdGroup>
                <Kbd class="px-0.5 py-0 min-w-0 h-auto">F5</Kbd>
              </KbdGroup>
            </div>
          </TooltipContent>
        </Tooltip>

        <div class="h-6 w-px bg-border/40" />

        <!-- Submit button -->
        <Tooltip :delay-duration="200">
          <TooltipTrigger as-child>
            <Button
              :aria-label="t('problem.layout.submitSolution')"
              :disabled="isSubmitting"
              class="group cursor-pointer gap-1.5 items-center px-3 disabled:opacity-50 focus:outline-none"
              @click="handleSubmit"
            >
              <CloudUpload
                class="h-3.5 w-3.5 text-primary-foreground"
                :class="isSubmitting && 'animate-bounce'"
              />
              <span class="truncate text-primary-foreground">
                {{
                  isSubmitting
                    ? t("problem.layout.formatting")
                    : t("problem.layout.submitSolution")
                }}
              </span>
            </Button>
          </TooltipTrigger>
          <TooltipContent class="h-auto w-auto p-2">
            <div class="flex items-center gap-1">
              <p class="text-xs leading-none">
                {{ t("problem.layout.submitSolution") }}
              </p>
              <KbdGroup>
                <Kbd class="px-0.5 py-0 min-w-0 h-auto">Ctrl</Kbd>
                <span class="text-xs">+</span>
                <Kbd class="px-0.5 py-0 min-w-0 h-auto">Enter</Kbd>
              </KbdGroup>
            </div>
          </TooltipContent>
        </Tooltip>
      </div>
    </div>

    <!-- Keyboard Shortcuts Modal -->
    <KeyboardShortcutsModal v-model:open="showShortcutsModal" />
  </div>
</template>
