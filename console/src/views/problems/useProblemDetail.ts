import { ref, watch, type Ref } from "vue";
import { useI18n } from "vue-i18n";
import { toast } from "vue-sonner";
import type { ProblemDetail } from "@/types/problem-detail";
import type { ProblemRunResult } from "@/types/test-results";
import { fetchProblemDetailById } from "@/api/problem-detail";
import { ApiError } from "@/utils/request";
import { problemHooks } from "@/hooks/problem-hooks";
import { useProblemRun } from "./composables/useProblemRun";

export function useProblemDetail(slug: Ref<string | null | undefined>) {
  const problem = ref<ProblemDetail | null>(null);
  const runResult = ref<ProblemRunResult | null>(null);
  const isLoading = ref(false);
  const { t } = useI18n();

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

  // Reset the run result whenever the problem changes so a stale result from
  // the previous problem never renders.
  watch(
    () => problem.value?.id,
    () => {
      runResult.value = null;
    },
  );

  // Delegate run orchestration to the Problem run module seam (case
  // selection, run lifecycle, hook ordering, rate-limit parsing).
  useProblemRun(problem, runResult);

  return {
    problem,
    runResult,
    isLoading,
    loadProblem,
  };
}
