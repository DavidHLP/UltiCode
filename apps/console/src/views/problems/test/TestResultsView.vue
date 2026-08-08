<script setup lang="ts">
import { computed, watch } from "vue";
import { CheckCircle2, Circle, ScrollText, XCircle } from "lucide-vue-next";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
} from "@/components/ui/empty";
import { useBottomPanelStore } from "./test";
import { useI18n } from "vue-i18n";
import type {
  ProblemCaseResultDetail,
  ProblemRunResult,
} from "@/types/test-results";
import {
  getCaseOutput,
  hasDisplayValue,
  hasResultDetails,
} from "./testResultDisplay";
import type { SemanticColor } from "@/shared/badge-config/src";
import {
  getStatusColor,
  getStatusLabelI18nKey,
  isFinal,
} from "@/shared/submission-status/src";

const props = defineProps<{
  runResult: ProblemRunResult | null;
}>();

const { t } = useI18n();
const { activeCaseLabel } = useBottomPanelStore();

const cases = computed<ProblemCaseResultDetail[]>(
  () => props.runResult?.cases ?? [],
);

watch(
  () => cases.value,
  (list) => {
    if (!list || !list.length) {
      activeCaseLabel.value = null;
      return;
    }
    const first = list[0];
    if (!activeCaseLabel.value && first) {
      activeCaseLabel.value = first.caseLabel;
    }
  },
  { immediate: true, deep: true },
);

const activeResult = computed<ProblemCaseResultDetail | undefined>(() => {
  if (!cases.value.length) return undefined;
  if (!activeCaseLabel.value) return cases.value[0];
  return (
    cases.value.find((r) => r.caseLabel === activeCaseLabel.value) ??
    cases.value[0]
  );
});

const activeOutput = computed(() =>
  activeResult.value ? getCaseOutput(activeResult.value) : "",
);

const hasActiveResultDetails = computed(
  () => activeResult.value != null && hasResultDetails(activeResult.value),
);

const verdictLabel = computed(() => {
  const verdict = props.runResult?.verdict;
  if (!verdict) return t("problem.layout.noVerdict");
  const key = getStatusLabelI18nKey(verdict);
  return key ? t(key) : verdict;
});

// Single SemanticColor → text-class map for this surface, fed by the shared
// verdict→color truth so TLE, Runtime Error, System Error, etc. no longer
// disagree with the submissions table. Covers every verdict incl. Sandbox
// Error (neutral); unknown statuses fall back to muted.
const SEMANTIC_TEXT_CLASS: Record<SemanticColor, string> = {
  success: "text-[var(--terminal-green)]",
  warning: "text-[var(--terminal-amber)]",
  error: "text-[var(--terminal-red)]",
  // `info` and `electric` previously collapsed to the same CSS class — the
  // shared verdict map does not currently emit `info`, but if a future verdict
  // maps to it we want a visibly distinct token rather than silently
  // shadowing `electric`.
  info: "text-[var(--terminal-cyan)]",
  purple: "text-[var(--terminal-purple)]",
  electric: "text-[var(--accent-electric)]",
  neutral: "text-muted-foreground",
};

const verdictClass = computed(() => {
  const verdict = props.runResult?.verdict;
  if (!verdict) return "text-muted-foreground";
  return SEMANTIC_TEXT_CLASS[getStatusColor(verdict)];
});

const caseStatusIconClass = (status: ProblemCaseResultDetail["status"]) =>
  SEMANTIC_TEXT_CLASS[getStatusColor(status)];

// `isFailureStatus` resolves to true for every settled, non-Accepted verdict.
// Built on the shared `isFinal` classifier so the membership list lives in
// one place (shared/submission-status) — adding a new verdict automatically
// participates without touching this surface.
const isFailureStatus = (status: ProblemCaseResultDetail["status"]) =>
  status !== "Accepted" && isFinal(status);

const selectCase = (label: string) => {
  activeCaseLabel.value = label;
};
</script>

<template>
  <div class="flex h-full flex-col gap-4">
    <Empty
      v-if="!props.runResult"
      class="border border-border bg-muted/40 dark:bg-muted/20"
    >
      <EmptyContent>
        <EmptyMedia variant="icon">
          <ScrollText class="h-6 w-6 text-muted-foreground" />
        </EmptyMedia>
        <EmptyHeader>
          <div class="text-base font-semibold text-foreground">
            {{ t("problem.layout.noTestResults") }}
          </div>
          <EmptyDescription>{{
            t("problem.layout.runCodeToSeeResults")
          }}</EmptyDescription>
        </EmptyHeader>
      </EmptyContent>
    </Empty>

    <template v-else>
      <div class="flex items-baseline justify-between gap-3">
        <div class="flex items-center gap-2">
          <span :class="['text-base font-semibold', verdictClass]">
            {{ verdictLabel }}
          </span>
        </div>
      </div>

      <div
        v-if="
          props.runResult?.verdict === 'Compile Error' &&
          (props.runResult.errorMessage ?? props.runResult.error_message)
        "
        class="rounded-none bg-[var(--terminal-red)]/10 border border-[var(--terminal-red)]/30 p-3 text-xs font-mono text-[var(--terminal-red)]"
      >
        {{ props.runResult.errorMessage ?? props.runResult.error_message }}
      </div>

      <div v-if="cases.length" class="flex flex-col gap-4">
        <div class="flex flex-wrap items-center gap-3">
          <Button
            v-for="result in cases"
            :key="result.id"
            :variant="
              result.caseLabel === activeCaseLabel ? 'secondary' : 'ghost'
            "
            size="sm"
            class="h-7 rounded-none px-3 text-xs font-medium"
            :class="
              result.caseLabel === activeCaseLabel
                ? 'text-foreground shadow-none'
                : 'text-muted-foreground hover:text-foreground'
            "
            @click="selectCase(result.caseLabel)"
          >
            <span class="mr-1 inline-flex items-center gap-1">
              <CheckCircle2
                v-if="result.status === 'Accepted'"
                class="h-3 w-3"
                :class="caseStatusIconClass(result.status)"
              />
              <XCircle
                v-else-if="isFailureStatus(result.status)"
                class="h-3 w-3"
                :class="caseStatusIconClass(result.status)"
              />
              <Circle
                v-else
                class="h-3 w-3"
                :class="caseStatusIconClass(result.status)"
              />
            </span>
            <span>{{ result.caseLabel }}</span>
          </Button>
        </div>

        <div
          v-if="activeResult && hasActiveResultDetails"
          class="space-y-4 text-xs md:text-sm"
        >
          <div class="space-y-3">
            <div class="space-y-2">
              <template v-if="activeResult.inputs?.length">
                <div
                  v-for="field in activeResult.inputs"
                  :key="field.id"
                  class="space-y-1"
                >
                  <div class="text-xs font-medium text-muted-foreground">
                    {{ field.label }} =
                  </div>
                  <Input
                    :model-value="field.value"
                    readonly
                    class="font-mono text-xs md:text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
                  />
                </div>
              </template>
              <p v-else class="text-muted-foreground">
                {{ t("problem.layout.noPredefinedInputs") }}
              </p>
            </div>

            <div v-if="hasDisplayValue(activeOutput)" class="space-y-2">
              <div class="text-xs font-medium text-muted-foreground">
                {{ t("problem.layout.output") }} =
              </div>
              <Input
                :model-value="activeOutput"
                readonly
                class="font-mono text-xs md:text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
              />
            </div>

            <div
              v-if="hasDisplayValue(activeResult.expectedOutput)"
              class="space-y-2"
            >
              <div class="text-xs font-medium text-muted-foreground">
                {{ t("problem.layout.expected") }} =
              </div>
              <Input
                :model-value="activeResult.expectedOutput"
                readonly
                class="font-mono text-xs md:text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
              />
            </div>
          </div>
        </div>

        <div
          v-else-if="activeResult"
          class="border border-dashed border-border bg-muted/30 px-3 py-2 text-xs text-muted-foreground"
        >
          {{ t("problem.layout.noResultDetails") }}
        </div>
      </div>
    </template>
  </div>
</template>
