import { ref, watch, type Ref } from "vue";
import { useI18n } from "vue-i18n";
import { toast } from "vue-sonner";
import type { ProblemDetail } from "@/types/problem-detail";
import type { ProblemRunResult } from "@/types/test-results";
import { fetchProblemDetailById } from "@/api/problem-detail";
import { ApiError } from "@/utils/request";
import { problemHooks } from "@/hooks/problem-hooks";
import { useBottomPanelStore } from "./test/test";
import { runSubmission } from "@/api/submission";
import { useProblemEditorStore } from "@/stores/problemEditorStore";
import { storeToRefs } from "pinia";

export function useProblemDetail(slug: Ref<string | null | undefined>) {
  const problem = ref<ProblemDetail | null>(null);
  const runResult = ref<ProblemRunResult | null>(null);
  const isLoading = ref(false);
  const { t } = useI18n();
  const bottomPanelStore = useBottomPanelStore();
  const editorStore = useProblemEditorStore();
  const { code, language } = storeToRefs(editorStore);

  const loadProblem = async (value: string) => {
    await problemHooks.emit("problem:load:before", { slug: value });
    isLoading.value = true;
    try {
      problem.value = await fetchProblemDetailById(value);
      await problemHooks.emit("problem:load:after", {
        slug: value,
        problem: problem.value,
      });
    } catch (error) {
      // D-14 friendly toast: ApiError 30001/40000 → "题目不存在"; others → console.error fallback
      if (
        error instanceof ApiError &&
        (error.code === 30001 || error.code === 40000)
      ) {
        toast.error(t("errors.problem.PROBLEM_30001"));
      } else {
        console.error("Failed to load problem detail", error);
      }
      problem.value = null;
      await problemHooks.emit("problem:load:error", { slug: value, error });
    } finally {
      isLoading.value = false;
    }
  };

  watch(
    slug,
    (value) => {
      if (!value) {
        problem.value = null;
        return;
      }
      void loadProblem(value);
    },
    { immediate: true },
  );

  watch(
    () => problem.value?.id,
    () => {
      runResult.value = null;
    },
  );

  watch(
    () => bottomPanelStore.lastRunToken.value,
    async () => {
      if (!problem.value) return;
      const currentCode = code.value;
      const currentLanguage = language.value || "javascript";
      if (!currentCode.trim()) {
        runResult.value = null;
        return;
      }

      const cases =
        bottomPanelStore.testCases.value.length > 0
          ? bottomPanelStore.testCases.value
          : (problem.value.testCases ?? []);
      await problemHooks.emit("problem:run:before", {
        problemId: problem.value.id,
        caseCount: cases.length,
      });
      bottomPanelStore.isRunning.value = true;
      try {
        const result = await runSubmission(problem.value.id, {
          language: currentLanguage,
          code: currentCode,
          testCases: cases,
        });
        runResult.value = result;
        await problemHooks.emit("problem:run:after", {
          problemId: problem.value.id,
          runResult: result,
        });
      } catch (error) {
        // Rate limit error handling - show user-friendly message with wait time
        if (error instanceof ApiError) {
          if (error.code === 429) { // TOO_MANY_REQUESTS
            const message = error.message || "Rate limit exceeded";
            const waitTimeMatch = message.match(/(\d+)\s+seconds?/);
            const waitSeconds = waitTimeMatch ? parseInt(waitTimeMatch[1]) : 60;

            toast.error(
              t("errors.rateLimitExceeded", { seconds: waitSeconds }),
              {
                duration: 5000,
                description: t("errors.rateLimitDescription", { seconds: waitSeconds })
              }
            );
          } else {
            toast.error(t("errors.runCodeFailed"));
          }
        }
        console.error("Failed to run submission", error);
        runResult.value = null;
        await problemHooks.emit("problem:run:error", {
          problemId: problem.value.id,
          error,
        });
      } finally {
        bottomPanelStore.isRunning.value = false;
      }
    },
  );

  return {
    problem,
    runResult,
    isLoading,
    loadProblem,
  };
}
