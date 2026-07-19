import { watch, type Ref } from "vue";
import { useI18n } from "vue-i18n";
import { toast } from "vue-sonner";
import type { ProblemDetail } from "@/types/problem-detail";
import type { ProblemRunResult } from "@/types/test-results";
import { ApiError } from "@/utils/request";
import { runSubmission } from "@/api/submission";
import { problemHooks } from "@/hooks/problem-hooks";
import { useBottomPanelStore } from "../test/test";
import { useProblemEditorStore } from "@/stores/problemEditorStore";
import { storeToRefs } from "pinia";

/** Fallback editor language when the editor store has none selected. */
const DEFAULT_RUN_LANGUAGE = "javascript";
/**
 * Wait time shown in the rate-limit toast when the backend's
 * "retry in N seconds" message is absent or unparseable.
 */
const RATE_LIMIT_FALLBACK_WAIT_SECONDS = 60;
/** Duration the rate-limit toast stays on screen. */
const RATE_LIMIT_TOAST_DURATION_MS = 5000;

/**
 * Problem run module (architecture-review candidate #2). Concentrates the
 * run policy that previously hid inside {@link useProblemDetail}'s watcher
 * into one intent-oriented seam: case selection (drafted cases or the
 * problem's defaults), the run lifecycle (isRunning, runResult), hook
 * ordering (problem:run:before/after/error), and the request failure policy
 * (rate-limit toast parsing, generic error toast). Rendering stays outside.
 *
 * @param problem    the loaded problem (run is skipped while null)
 * @param runResult  the ref to stamp with the latest run result
 */
export function useProblemRun(
  problem: Ref<ProblemDetail | null>,
  runResult: Ref<ProblemRunResult | null>,
) {
  const { t } = useI18n();
  const bottomPanelStore = useBottomPanelStore();
  const editorStore = useProblemEditorStore();
  const { code, language } = storeToRefs(editorStore);

  // Run orchestration: a change to lastRunToken signals intent to run the
  // current code against the current case set. Cases come from the drafted
  // bottom-panel set when present, else the problem's built-in test cases.
  watch(
    () => bottomPanelStore.lastRunToken.value,
    async () => {
      if (!problem.value) return;
      const currentCode = code.value;
      const currentLanguage = language.value || DEFAULT_RUN_LANGUAGE;
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
          if (error.code === 429) {
            // TOO_MANY_REQUESTS
            const message = error.message || "Rate limit exceeded";
            const waitTimeMatch = message.match(/(\d+)\s+seconds?/);
            const waitSeconds = waitTimeMatch
              ? parseInt(waitTimeMatch[1])
              : RATE_LIMIT_FALLBACK_WAIT_SECONDS;

            toast.error(t("errors.rateLimitExceeded", { seconds: waitSeconds }), {
              duration: RATE_LIMIT_TOAST_DURATION_MS,
              description: t("errors.rateLimitDescription", {
                seconds: waitSeconds,
              }),
            });
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
}
