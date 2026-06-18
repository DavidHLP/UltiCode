<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { Play, CloudUpload } from "lucide-vue-next";
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
  requestRun();
  headerStore.setActiveGroup("test-info");
  headerStore.setActiveHeader("test-info", 6);
  runPulseKey.value = Date.now();
};

async function handleSubmit() {
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
  <div
    class="relative z-20 flex min-w-60 flex-1 items-center overflow-hidden pointer-events-auto"
  >
    <div
      class="flex items-center overflow-hidden rounded-none focus:outline-none"
    >
      <div class="relative group/nav-back flex items-center gap-2">
        <!-- Run button with HoverCard -->
        <HoverCard :open-delay="200">
          <HoverCardTrigger as-child>
            <Button
              :aria-label="t('problem.layout.runCode')"
              :aria-busy="bottomPanelStore.isRunning"
              class="group flex cursor-pointer gap-1.5 items-center h-8 transition-all duration-200 text-[var(--solarized-green)] px-3 bg-[var(--solarized-base3)] dark:bg-[var(--solarized-base02)] border border-[var(--solarized-green)]/40 hover:bg-[var(--solarized-green)] hover:text-white dark:hover:text-[var(--solarized-base03)] hover:border-[var(--solarized-green)] disabled:opacity-50 rounded-none focus:outline-none focus:ring-0 focus:ring-offset-0 font-bold uppercase tracking-wider text-[11px]"
              @click="handleRun"
            >
              <Play
                class="h-3.5 w-3.5 transition-transform duration-200"
                :class="
                  bottomPanelStore.isRunning
                    ? 'text-current animate-[spin_0.9s_linear]'
                    : 'text-current'
                "
              />
              <span class="truncate">{{ t("problem.layout.runCode") }}</span>
            </Button>
          </HoverCardTrigger>
          <HoverCardContent class="h-auto w-auto p-2 rounded-none">
            <div class="flex items-center gap-1">
              <p class="text-xs leading-none">
                {{ t("problem.layout.runCode") }}
              </p>
              <KbdGroup class="text-xs">
                <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs rounded-none"
                  >F5</Kbd
                >
              </KbdGroup>
            </div>
          </HoverCardContent>
        </HoverCard>

        <div class="h-6 w-px bg-border/40" />

        <!-- Submit button -->
        <HoverCard :open-delay="200">
          <HoverCardTrigger as-child>
            <Button
              :aria-label="t('problem.layout.submitSolution')"
              :disabled="isSubmitting"
              class="group cursor-pointer gap-1.5 items-center h-8 transition-all duration-200 text-white px-3 bg-[var(--accent-electric)] border border-[var(--accent-electric)] hover:bg-[var(--accent-electric)]/95 hover:border-[var(--accent-electric)]/95 disabled:opacity-50 rounded-none focus:outline-none focus:ring-0 focus:ring-offset-0 font-bold uppercase tracking-wider text-[11px] shadow-sm"
              @click="handleSubmit"
            >
              <CloudUpload
                class="h-3.5 w-3.5 text-white"
                :class="isSubmitting && 'animate-bounce'"
              />
              <span class="truncate text-white">
                {{
                  isSubmitting
                    ? t("problem.layout.formatting")
                    : t("problem.layout.submitSolution")
                }}
              </span>
            </Button>
          </HoverCardTrigger>
          <HoverCardContent class="h-auto w-auto p-2 rounded-none">
            <div class="flex items-center gap-1">
              <p class="text-xs leading-none">
                {{ t("problem.layout.submitSolution") }}
              </p>
              <KbdGroup class="text-xs">
                <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs rounded-none"
                  >Ctrl</Kbd
                >
                <span class="text-xs">+</span>
                <Kbd class="px-0.5 py-0 min-w-0 h-auto text-xs rounded-none"
                  >Enter</Kbd
                >
              </KbdGroup>
            </div>
          </HoverCardContent>
        </HoverCard>
      </div>
    </div>

    <!-- Keyboard Shortcuts Modal -->
    <KeyboardShortcutsModal v-model:open="showShortcutsModal" />
  </div>
</template>
